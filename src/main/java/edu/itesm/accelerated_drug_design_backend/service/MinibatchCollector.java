package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinibatchCollector {

	private static final Logger log = LoggerFactory.getLogger(MinibatchCollector.class);
	private static final int LOG_SUMMARY_EVERY = 10;

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreSystemInterface coreSystem;
	private final CoreMetricsPoller metricsPoller;
	private final MinibatchPersistenceService persistenceService;
	private final Map<Long, RepeatedFailure> repeatedFailures = new ConcurrentHashMap<>();

	public MinibatchCollector(
			GenerationJobMinibatchRepository minibatchRepository,
			CoreInstanceRepository coreInstanceRepository,
			CoreSystemInterface coreSystem,
			CoreMetricsPoller metricsPoller,
			MinibatchPersistenceService persistenceService) {
		this.minibatchRepository = minibatchRepository;
		this.coreInstanceRepository = coreInstanceRepository;
		this.coreSystem = coreSystem;
		this.metricsPoller = metricsPoller;
		this.persistenceService = persistenceService;
	}

	@Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:15000}")
	public void collect() {
		List<GenerationJobMinibatch> running = minibatchRepository.findByStatusOrderByJobThenIdx(
				GenerationJobMinibatch.STATUS_RUNNING);
		for (GenerationJobMinibatch minibatch : running) {
			try {
				collectOne(minibatch.getId());
				repeatedFailures.remove(minibatch.getId());
			} catch (Exception e) {
				logRepeated(minibatch.getId(), e);
			}
		}
	}

	private void collectOne(Long minibatchId) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		CoreInstance instance = minibatch.getCoreInstanceId() == null
				? null
				: coreInstanceRepository.findById(minibatch.getCoreInstanceId()).orElse(null);
		if (instance == null || CoreInstance.STATUS_UNAVAILABLE.equals(instance.getStatus())) {
			if (instance != null && coreRunStillRunning(instance, minibatch.getRunId())) {
				log.info("Minibatch {} stays RUNNING: instance {} is UNAVAILABLE but the core run is still RUNNING",
						minibatch.getRunId(), instance.getId());
				return;
			}
			persistenceService.markCoreFailure(
					minibatchId,
					instance == null ? "core instance missing" : "core instance UNAVAILABLE");
			return;
		}
		Map<String, Object> response = coreSystem.getMpnnStatus(instance, minibatch.getRunId());
		if (response == null) {
			metricsPoller.recordUnreachable(instance.getId(), "status unreachable");
			CoreInstance fresh = coreInstanceRepository.findById(instance.getId()).orElse(null);
			if (fresh != null && CoreInstance.STATUS_UNAVAILABLE.equals(fresh.getStatus())) {
				persistenceService.markCoreFailure(minibatchId, "core unreachable");
			}
			return;
		}
		metricsPoller.recordReachable(instance.getId());
		String status = text(response, "status");
		if (status == null) {
			return;
		}
		status = status.toUpperCase();
		String errorDetails = text(response, "error_details");
		if ("RUNNING".equals(status)) {
			return;
		}
		if ("ERROR".equals(status) || (errorDetails != null && errorDetails.toLowerCase().contains("orphaned"))) {
			persistenceService.markCoreFailure(minibatchId, errorDetails != null ? errorDetails : "core error");
			return;
		}
		if (!"COMPLETED".equals(status)) {
			return;
		}
		String fasta = fastaOf(response);
		int batches = batchesOf(response);
		List<Map<String, Object>> details = new ArrayList<>();
		for (int batch = 0; batch < batches; batch++) {
			Map<String, Object> detail = coreSystem.getMpnnStatusDetail(instance, minibatch.getRunId(), batch);
			if (detail != null) {
				details.add(detail);
			}
		}
		persistenceService.persistCompleted(minibatchId, fasta, details, paramDetailsOf(response));
	}

	private boolean coreRunStillRunning(CoreInstance instance, String runId) {
		Map<String, Object> status = coreSystem.getMpnnStatus(instance, runId);
		return status != null && "RUNNING".equalsIgnoreCase(text(status, "status"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> paramDetailsOf(Map<String, Object> response) {
		Object summary = response.get("summary");
		if (!(summary instanceof Map<?, ?> map)) {
			return null;
		}
		Object details = map.get("param_details");
		if (details instanceof Map<?, ?> paramDetails) {
			return (Map<String, Object>) paramDetails;
		}
		return null;
	}

	private void logRepeated(Long minibatchId, Exception error) {
		String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
		RepeatedFailure previous = repeatedFailures.get(minibatchId);
		if (previous == null || !previous.message.equals(message)) {
			repeatedFailures.put(minibatchId, new RepeatedFailure(message, 1));
			log.warn("Collect minibatch {} failed: {}", minibatchId, message, error);
			return;
		}
		int count = previous.count + 1;
		repeatedFailures.put(minibatchId, new RepeatedFailure(message, count));
		if (count % LOG_SUMMARY_EVERY == 0) {
			log.warn("Collect minibatch {} still failing ({} cycles): {}", minibatchId, count, message);
		}
	}

	@SuppressWarnings("unchecked")
	private static String fastaOf(Map<String, Object> response) {
		Object summary = response.get("summary");
		if (summary instanceof Map<?, ?> map && map.get("fasta_content") != null) {
			return String.valueOf(map.get("fasta_content"));
		}
		Object fasta = response.get("output_fasta");
		return fasta == null ? null : String.valueOf(fasta);
	}

	@SuppressWarnings("unchecked")
	private static int batchesOf(Map<String, Object> response) {
		Object pagination = response.get("pagination");
		if (pagination instanceof Map<?, ?> map && map.get("total_batches") instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		Object top = response.get("total_batches");
		if (top instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		return 0;
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private record RepeatedFailure(String message, int count) {}
}
