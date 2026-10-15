package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reconciles occupied core instances, reaps stale claims, and dispatches QUEUED runs.
 * Outer methods are not transactional: HTTP to the core stays outside DB transactions.
 */
@Service
public class CoreDispatchService {

	private static final Logger log = LoggerFactory.getLogger(CoreDispatchService.class);
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_ERROR = "ERROR";
	private static final String STATUS_RUNNING = "RUNNING";
	private static final String STATUS_QUEUED = "QUEUED";

	private final CoreInstanceRepository coreInstanceRepository;
	private final InstanceAllocator allocator;
	private final CoreSystemInterface coreSystem;
	private final BackboneService backboneService;
	private final GenerationJobService generationJobService;
	private final CoreInstanceHealthService healthService;
	private final int staleClaimMinutes;
	private final int dispatchMaxAttempts;
	private final long dispatchBackoffMs;

	public CoreDispatchService(
			CoreInstanceRepository coreInstanceRepository,
			InstanceAllocator allocator,
			CoreSystemInterface coreSystem,
			BackboneService backboneService,
			GenerationJobService generationJobService,
			CoreInstanceHealthService healthService,
			@Value("${app.core.stale-claim-minutes:240}") int staleClaimMinutes,
			@Value("${app.core.dispatch-max-attempts:3}") int dispatchMaxAttempts,
			@Value("${app.core.dispatch-backoff-ms:30000}") long dispatchBackoffMs) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.allocator = allocator;
		this.coreSystem = coreSystem;
		this.backboneService = backboneService;
		this.generationJobService = generationJobService;
		this.healthService = healthService;
		this.staleClaimMinutes = staleClaimMinutes;
		this.dispatchMaxAttempts = Math.max(1, dispatchMaxAttempts);
		this.dispatchBackoffMs = Math.max(0, dispatchBackoffMs);
	}

	public void reconcileOccupied() {
		for (CoreInstance instance : coreInstanceRepository.findByCurrentRunIdIsNotNull()) {
			reconcileInstance(instance, false);
		}
	}

	public void reapStaleClaims() {
		Instant cutoff = Instant.now().minus(Duration.ofMinutes(staleClaimMinutes));
		for (CoreInstance instance : coreInstanceRepository.findByCurrentRunIdIsNotNull()) {
			if (instance.getClaimedAt() == null || !instance.getClaimedAt().isBefore(cutoff)) {
				continue;
			}
			reconcileInstance(instance, true);
		}
	}

	public void dispatchQueued() {
		Instant now = Instant.now();
		ZonedDateTime nowZoned = allocator.now();
		List<CoreInstance> instances = coreInstanceRepository.findAllWithWindows();
		List<QueuedRun> queued = new ArrayList<>();
		for (String runId : backboneService.findQueuedRunIdsReady(now)) {
			queued.add(new QueuedRun(runId, CoreInstance.RUN_KIND_BACKBONE));
		}
		for (GenerationJob job : generationJobService.findQueuedReady(now)) {
			queued.add(new QueuedRun(job.getRunId(), CoreInstance.RUN_KIND_GENERATION_JOB));
		}

		for (QueuedRun run : queued) {
			if (coreInstanceRepository.findByCurrentRunId(run.runId).isPresent()) {
				continue;
			}
			CoreInstance claimed = claimEligible(instances, run, nowZoned);
			if (claimed == null) {
				continue;
			}
			try {
				int marked = markRunning(run, claimed.getId());
				if (marked == 0) {
					allocator.release(claimed.getId(), run.runId);
					claimed.setCurrentRunId(null);
					claimed.setCurrentRunKind(null);
					claimed.setClaimedAt(null);
					log.warn("Run {} was no longer QUEUED after claim; released instance {}", run.runId, claimed.getId());
					continue;
				}
				trigger(run, claimed);
			} catch (Exception e) {
				String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				log.warn("Dispatch POST failed for {} {}: {}", run.kind, run.runId, err, e);
				allocator.release(claimed.getId(), run.runId);
				claimed.setCurrentRunId(null);
				claimed.setCurrentRunKind(null);
				claimed.setClaimedAt(null);
				requeueOrFail(run, err);
			}
		}
	}

	private void reconcileInstance(CoreInstance instance, boolean staleReaper) {
		String runId = instance.getCurrentRunId();
		if (runId == null || runId.isBlank()) {
			return;
		}
		String kind = resolveKind(instance, runId);
		String coreStatus = pollCoreStatus(instance, runId, kind);

		if (STATUS_RUNNING.equals(coreStatus)) {
			syncLocalRunning(instance, runId, kind);
			return;
		}
		if (STATUS_COMPLETED.equals(coreStatus) || STATUS_ERROR.equals(coreStatus)) {
			ingest(instance, runId, kind);
			allocator.release(instance.getId(), runId);
			log.info("Released instance {} after core status {} for run {}", instance.getId(), coreStatus, runId);
			return;
		}

		if (coreStatus == null) {
			markHealthDown(instance);
			if (staleReaper) {
				requeueOrFail(new QueuedRun(runId, kind), "Stale claim: run missing or core unreachable");
				allocator.release(instance.getId(), runId);
				log.warn("Reaped stale claim on instance {} for run {}", instance.getId(), runId);
			}
		}
	}

	private CoreInstance claimEligible(List<CoreInstance> instances, QueuedRun run, ZonedDateTime nowZoned) {
		for (CoreInstance instance : instances) {
			if (!allocator.isDispatchEligible(instance, nowZoned)) {
				continue;
			}
			if (!allocator.tryClaim(instance.getId(), run.runId, run.kind)) {
				continue;
			}
			instance.setCurrentRunId(run.runId);
			instance.setCurrentRunKind(run.kind);
			instance.setClaimedAt(Instant.now());
			return instance;
		}
		return null;
	}

	private int markRunning(QueuedRun run, Long instanceId) {
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(run.kind)) {
			return generationJobService.markRunningIfQueued(run.runId, instanceId);
		}
		return backboneService.markRunningIfQueued(run.runId, instanceId);
	}

	private void trigger(QueuedRun run, CoreInstance instance) {
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(run.kind)) {
			generationJobService.triggerQueuedMpnn(run.runId, instance);
		} else {
			backboneService.triggerQueuedRfdiffusion(run.runId, instance);
		}
	}

	private void ingest(CoreInstance instance, String runId, String kind) {
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(kind)) {
			generationJobService.findByRunId(runId).ifPresent(job ->
					generationJobService.checkRunStatus(job.getProject().getId(), runId, instance));
			return;
		}
		backboneService.findProjectIdByRunId(runId).ifPresent(projectId ->
				backboneService.checkRunStatus(projectId, runId, instance));
	}

	private void syncLocalRunning(CoreInstance instance, String runId, String kind) {
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(kind)) {
			generationJobService.findByRunId(runId)
					.filter(job -> STATUS_QUEUED.equals(job.getStatus()))
					.ifPresent(job -> generationJobService.markRunningIfQueued(runId, instance.getId()));
			return;
		}
		List<Backbone> backbones = backboneService.findByRunId(runId);
		if (!backbones.isEmpty() && backbones.stream().allMatch(bb -> STATUS_QUEUED.equals(bb.getStatus()))) {
			backboneService.markRunningIfQueued(runId, instance.getId());
		}
	}

	private void requeueOrFail(QueuedRun run, String error) {
		int nextAttempts = currentAttempts(run) + 1;
		Instant nextAt = Instant.now().plusMillis(backoffDelayMs(nextAttempts));
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(run.kind)) {
			generationJobService.requeueOrFail(run.runId, error, dispatchMaxAttempts, nextAt);
		} else {
			backboneService.requeueOrFail(run.runId, error, dispatchMaxAttempts, nextAt);
		}
	}

	private int currentAttempts(QueuedRun run) {
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(run.kind)) {
			return generationJobService.findByRunId(run.runId)
					.map(GenerationJob::getDispatchAttempts)
					.orElse(0);
		}
		return backboneService.findByRunId(run.runId).stream()
				.findFirst()
				.map(Backbone::getDispatchAttempts)
				.orElse(0);
	}

	private long backoffDelayMs(int attemptsAfterFailure) {
		int exp = Math.max(0, attemptsAfterFailure - 1);
		int capped = Math.min(exp, 16);
		return dispatchBackoffMs * (1L << capped);
	}

	private String resolveKind(CoreInstance instance, String runId) {
		if (instance.getCurrentRunKind() != null && !instance.getCurrentRunKind().isBlank()) {
			return instance.getCurrentRunKind();
		}
		if (generationJobService.findByRunId(runId).isPresent()) {
			return CoreInstance.RUN_KIND_GENERATION_JOB;
		}
		return CoreInstance.RUN_KIND_BACKBONE;
	}

	private String pollCoreStatus(CoreInstance instance, String runId, String kind) {
		Map<String, Object> response;
		if (CoreInstance.RUN_KIND_GENERATION_JOB.equals(kind)) {
			response = coreSystem.getMpnnStatus(instance, runId);
		} else {
			response = coreSystem.getRfdiffusionStatus(instance, runId);
		}
		if (response == null) {
			return null;
		}
		Object raw = response.get("status");
		if (raw == null) {
			return null;
		}
		String status = String.valueOf(raw).toUpperCase().strip();
		return status.isBlank() ? null : status;
	}

	private void markHealthDown(CoreInstance instance) {
		healthService.persistHealth(instance.getId(), CoreInstance.HEALTH_DOWN);
		log.warn("Core instance {} marked DOWN (occupied run {} unreachable)", instance.getId(), instance.getCurrentRunId());
	}

	private record QueuedRun(String runId, String kind) {
	}
}
