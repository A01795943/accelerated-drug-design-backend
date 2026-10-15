package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.core.RunPresence;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class MinibatchDispatcher {

	private static final Logger log = LoggerFactory.getLogger(MinibatchDispatcher.class);
	private static final int IDLE_LOG_EVERY = 10;

	private static final int PROBE_TIMEOUT_MS = 4_000;

	private int idleCycles;
	private final ExecutorService probePool = Executors.newFixedThreadPool(4);
	private final RepeatedLog repeatedLog = new RepeatedLog(10);

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final CoreInstanceRepository coreInstanceRepository;
	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobService generationJobService;
	private final CoreMetricsPoller metricsPoller;
	private final MinibatchClaimService claimService;
	private final MinibatchAttemptService attemptService;
	private final CoreSystemInterface coreSystem;
	private final SettingsService settingsService;
	private final BinderKeyService binderKeyService;

	public MinibatchDispatcher(
			GenerationJobMinibatchRepository minibatchRepository,
			CoreInstanceRepository coreInstanceRepository,
			GenerationJobRepository generationJobRepository,
			GenerationJobService generationJobService,
			CoreMetricsPoller metricsPoller,
			MinibatchClaimService claimService,
			MinibatchAttemptService attemptService,
			CoreSystemInterface coreSystem,
			SettingsService settingsService,
			BinderKeyService binderKeyService) {
		this.minibatchRepository = minibatchRepository;
		this.coreInstanceRepository = coreInstanceRepository;
		this.generationJobRepository = generationJobRepository;
		this.generationJobService = generationJobService;
		this.metricsPoller = metricsPoller;
		this.claimService = claimService;
		this.attemptService = attemptService;
		this.coreSystem = coreSystem;
		this.settingsService = settingsService;
		this.binderKeyService = binderKeyService;
	}

	@Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:15000}")
	public void dispatchPending() {
		List<GenerationJobMinibatch> pending = minibatchRepository.findByStatusOrderByJobThenIdx(
				GenerationJobMinibatch.STATUS_PENDING);
		String instances = instanceSnapshot();
		log.info("Dispatch cycle: pending={} {}", pending.size(), instances);
		int dispatched = 0;
		String blocked = null;
		Set<Long> unresponsive = new HashSet<>();
		for (GenerationJobMinibatch minibatch : pending) {
			try {
				DispatchResult result = dispatchOne(minibatch, unresponsive);
				if (result == DispatchResult.DISPATCHED) {
					dispatched++;
				} else if (result == DispatchResult.NO_INSTANCE) {
					blocked = "no eligible instance";
					break;
				}
			} catch (Exception e) {
				repeatedLog.warn(log, "dispatch-" + minibatch.getId(),
						"Minibatch " + minibatch.getId() + " dispatch error: " + e.getMessage(), e);
			}
		}
		if (pending.isEmpty() || dispatched == 0) {
			idleCycles++;
			if (idleCycles % IDLE_LOG_EVERY == 0) {
				String reason = pending.isEmpty() ? "no PENDING minibatches" : (blocked != null ? blocked : "nothing claimed");
				log.warn("Dispatch cycle sent nothing ({} cycles): {} {}", idleCycles, reason, instances);
			}
		} else {
			idleCycles = 0;
		}
	}

	private String instanceSnapshot() {
		StringBuilder sb = new StringBuilder();
		for (CoreMetricsPoller.InstanceLoad row : metricsPoller.describeInstances()) {
			if (!sb.isEmpty()) {
				sb.append(" | ");
			}
			sb.append("id=").append(row.id())
					.append(" status=").append(row.status())
					.append(" enabled=").append(row.enabled())
					.append(" inFlight=").append(row.inFlight()).append('/').append(row.maxConcurrent());
			if (row.skipReason() != null) {
				sb.append(" skip=").append(row.skipReason());
			}
		}
		return sb.toString();
	}

	@PreDestroy
	void shutdownProbePool() {
		probePool.shutdownNow();
	}

	private DispatchResult dispatchOne(GenerationJobMinibatch minibatch, Set<Long> unresponsive) {
		CoreInstance alreadyRunning = locateExistingRun(minibatch.getRunId(), unresponsive);
		if (alreadyRunning != null) {
			if (claimService.adopt(minibatch.getId(), alreadyRunning.getId())) {
				log.info("Adopted minibatch {} runId={} already present on instance {}",
						minibatch.getId(), minibatch.getRunId(), alreadyRunning.getId());
				return DispatchResult.DISPATCHED;
			}
			return DispatchResult.SKIPPED;
		}
		CoreInstance instance = metricsPoller.pickEligible();
		if (instance == null) {
			return DispatchResult.NO_INSTANCE;
		}
		GenerationJob job = generationJobRepository.findByIdWithBackbone(minibatch.getGenerationJobId()).orElse(null);
		if (job == null || job.getBackbone() == null) {
			return DispatchResult.SKIPPED;
		}
		if (!claimService.claim(minibatch.getId(), instance.getId())) {
			return DispatchResult.SKIPPED;
		}
		GenerationJobMinibatch claimed = minibatchRepository.findById(minibatch.getId()).orElse(null);
		ProteinMpnnRunRequest request = requestFor(job, minibatch);
		try {
			if (claimed != null) {
				attemptService.open(claimed.getId(), claimed.getAttempts(), instance.getId(), claimed.getStartedAt());
			}
			coreSystem.triggerMpnn(instance, request);
		} catch (Exception e) {
			String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			log.warn("POST /run/mpnn failed for {} on instance {}: {}", minibatch.getRunId(), instance.getId(), message);
			attemptService.finishOpen(minibatch.getId(), GenerationJobMinibatchAttempt.OUTCOME_FAILED, message);
			claimService.revertUnsentClaim(minibatch.getId());
			metricsPoller.recordUnreachable(instance.getId(), message);
			return DispatchResult.FAILED;
		}
		try {
			generationJobService.markRunningIfStillQueued(job.getId());
		} catch (RuntimeException ex) {
			log.warn("Minibatch {} was posted but the job status update failed: {}", minibatch.getId(), ex.getMessage());
		}
		metricsPoller.recordReachable(instance.getId());
		log.info("Dispatched minibatch {} runId={} to instance {} maxAttempts={}",
				minibatch.getId(), minibatch.getRunId(), instance.getId(), request.getMaxAttempts());
		return DispatchResult.DISPATCHED;
	}

	private CoreInstance locateExistingRun(String runId, Set<Long> unresponsive) {
		if (runId == null || runId.isBlank()) {
			return null;
		}
		List<CoreInstance> instances = coreInstanceRepository.findByEnabledTrue().stream()
				.filter(instance -> !unresponsive.contains(instance.getId()))
				.toList();
		if (instances.isEmpty()) {
			return null;
		}
		List<CompletableFuture<ProbeHit>> probes = new ArrayList<>();
		for (CoreInstance instance : instances) {
			probes.add(CompletableFuture.supplyAsync(() -> new ProbeHit(instance, coreSystem.probeMpnnRun(instance, runId)), probePool)
					.completeOnTimeout(new ProbeHit(instance, RunPresence.UNKNOWN), PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
		}
		CoreInstance present = null;
		for (CompletableFuture<ProbeHit> probe : probes) {
			ProbeHit hit = probe.join();
			if (hit.presence == RunPresence.UNKNOWN) {
				unresponsive.add(hit.instance.getId());
			} else if (hit.presence == RunPresence.PRESENT && present == null) {
				present = hit.instance;
			}
		}
		return present;
	}

	private record ProbeHit(CoreInstance instance, RunPresence presence) {
	}

	private enum DispatchResult {
		DISPATCHED, NO_INSTANCE, FAILED, SKIPPED
	}

	private ProteinMpnnRunRequest requestFor(GenerationJob job, GenerationJobMinibatch minibatch) {
		ProteinMpnnRunRequest request = new ProteinMpnnRunRequest();
		request.setRunId(minibatch.getRunId());
		request.setRunName(minibatch.getRunId());
		request.setPdbContent(job.getBackbone().getStructure() != null ? job.getBackbone().getStructure() : "");
		request.setContigs(job.getBackbone().getContigs() != null ? job.getBackbone().getContigs() : "");
		request.setNumSeqs(minibatch.getNumSeqs());
		request.setMpnnSamplingTemp(job.getTemperature() != null ? job.getTemperature() : 0.1);
		request.setUseAlphafold(true);
		request.setSeed(MpnnSampling.seedFor(job.getId(), minibatch.getNOffset()));
		int numSeqs = Math.max(minibatch.getNumSeqs(), 0);
		request.setMaxAttempts(numSeqs * settingsService.maxAttemptFactor());
		request.setExcludeBinderSeqs(binderKeyService.keysToExclude(
				job.getBackbone().getId(), minibatch.getId()));
		return request;
	}
}
