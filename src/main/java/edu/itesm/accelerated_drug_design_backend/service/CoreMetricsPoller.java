package edu.itesm.accelerated_drug_design_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.itesm.accelerated_drug_design_backend.config.CoreLoadProperties;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceMetricsHistoryRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@EnableConfigurationProperties(CoreLoadProperties.class)
public class CoreMetricsPoller {

	private static final Logger log = LoggerFactory.getLogger(CoreMetricsPoller.class);
	private static final int METRICS_TIMEOUT_MS = 5000;

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreInstanceMetricsHistoryRepository historyRepository;
	private final GenerationJobMinibatchRepository minibatchRepository;
	private final CoreSystemInterface coreSystem;
	private final CoreLoadProperties loadProperties;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final int maxFailures;
	private final int retentionDays;

	public CoreMetricsPoller(
			CoreInstanceRepository coreInstanceRepository,
			CoreInstanceMetricsHistoryRepository historyRepository,
			GenerationJobMinibatchRepository minibatchRepository,
			CoreSystemInterface coreSystem,
			CoreLoadProperties loadProperties,
			@Value("${app.core.metrics.max-failures:3}") int maxFailures,
			@Value("${app.core.metrics.retention-days:7}") int retentionDays) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.historyRepository = historyRepository;
		this.minibatchRepository = minibatchRepository;
		this.coreSystem = coreSystem;
		this.loadProperties = loadProperties;
		this.maxFailures = Math.max(1, maxFailures);
		this.retentionDays = Math.max(1, retentionDays);
	}

	@Scheduled(fixedDelayString = "${app.core.metrics.poll-interval-ms:300000}")
	public void poll() {
		List<CoreInstance> enabled = coreInstanceRepository.findByEnabledTrue();
		if (enabled.isEmpty()) {
			return;
		}
		ExecutorService pool = Executors.newFixedThreadPool(Math.min(enabled.size(), 8));
		try {
			for (CoreInstance instance : enabled) {
				pool.submit(() -> pollIsolated(instance));
			}
		} finally {
			pool.shutdown();
			try {
				pool.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void refresh(Long instanceId) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId)
				.orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Core instance not found: " + instanceId));
		pollIsolated(instance);
	}

	@Scheduled(cron = "${app.core.metrics.retention-cron:0 30 3 * * *}")
	@Transactional
	public void purgeOldHistory() {
		Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
		long removed = historyRepository.deleteByCollectedAtBefore(cutoff);
		if (removed > 0) {
			log.info("Deleted {} core metric samples older than {} days", removed, retentionDays);
		}
	}

	private void pollIsolated(CoreInstance snapshot) {
		try {
			Map<String, Object> metrics = coreSystem.getSystemMetrics(snapshot, METRICS_TIMEOUT_MS, METRICS_TIMEOUT_MS);
			apply(snapshot.getId(), metrics);
		} catch (Exception e) {
			log.warn("Metrics poll failed for instance {}: {}", snapshot.getId(), e.getMessage());
			apply(snapshot.getId(), null);
		}
	}

	@Transactional
	public void apply(Long instanceId, Map<String, Object> metrics) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId).orElse(null);
		if (instance == null) {
			return;
		}
		if (metrics == null) {
			int failures = instance.getConsecutiveFailures() + 1;
			instance.setConsecutiveFailures(failures);
			if (failures >= maxFailures && !CoreInstance.STATUS_UNAVAILABLE.equals(instance.getStatus())) {
				log.info("Core instance {} {} -> UNAVAILABLE (metrics failures={})",
						instance.getName(), instance.getStatus(), failures);
				instance.setStatus(CoreInstance.STATUS_UNAVAILABLE);
				instance.setStatusReason("metrics unreachable");
			}
			coreInstanceRepository.save(instance);
			return;
		}

		String previous = instance.getStatus();
		String basis = CoreInstance.STATUS_UNAVAILABLE.equals(previous)
				? CoreInstance.STATUS_AVAILABLE
				: previous;
		CoreLoadPolicy.Decision decision = CoreLoadPolicy.decide(
				basis, metrics, loadProperties.enter(), loadProperties.recover());
		if (!decision.status().equals(previous)) {
			log.info("Core instance {} {} -> {} ({})",
					instance.getName(), previous, decision.status(), decision.reason());
		}
		instance.setConsecutiveFailures(0);
		instance.setStatus(decision.status());
		instance.setStatusReason(decision.reason());
		instance.setLastMetricsAt(Instant.now());
		instance.setLastMetricsJson(writeJson(metrics));
		coreInstanceRepository.save(instance);

		CoreInstanceMetricsHistory row = new CoreInstanceMetricsHistory();
		row.setCoreInstanceId(instance.getId());
		row.setCollectedAt(instance.getLastMetricsAt());
		row.setCpuPercent(CoreLoadPolicy.nestedPercent(metrics, "cpu"));
		row.setMemPercent(CoreLoadPolicy.nestedPercent(metrics, "memory"));
		row.setGpuUtilPercent(CoreLoadPolicy.maxGpu(metrics, "util_percent"));
		row.setGpuMemPercent(CoreLoadPolicy.maxGpu(metrics, "mem_percent"));
		row.setDiskPercent(CoreLoadPolicy.nestedPercent(metrics, "disk"));
		Object runs = metrics.get("runs");
		if (runs instanceof Map<?, ?> runMap && runMap.get("running_count") instanceof Number number) {
			row.setRunningCount(number.intValue());
		}
		Object network = metrics.get("network");
		if (network instanceof Map<?, ?> net) {
			if (net.get("sent_per_sec") instanceof Number sent) {
				row.setNetSentPerSec(sent.doubleValue());
			}
			if (net.get("recv_per_sec") instanceof Number recv) {
				row.setNetRecvPerSec(recv.doubleValue());
			}
		}
		historyRepository.save(row);
	}

	@Transactional(readOnly = true)
	public boolean isEligible(CoreInstance instance) {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		if (!CoreInstance.STATUS_AVAILABLE.equals(instance.getStatus())) {
			return false;
		}
		if (CoreInstance.HEALTH_DOWN.equals(instance.getHealthState())) {
			return false;
		}
		long inFlight = minibatchRepository.countByCoreInstanceIdAndStatus(
				instance.getId(), GenerationJobMinibatch.STATUS_RUNNING);
		if (instance.getCurrentRunId() != null && !instance.getCurrentRunId().isBlank()
				&& minibatchRepository.findByRunId(instance.getCurrentRunId()).isEmpty()) {
			inFlight += 1;
		}
		int max = Math.max(1, instance.getMaxConcurrentRuns());
		return inFlight < max;
	}

	public CoreInstance pickEligible() {
		return coreInstanceRepository.findByEnabledTrue().stream()
				.filter(this::isEligible)
				.min((a, b) -> {
					int byLoad = Long.compare(inFlight(a), inFlight(b));
					if (byLoad != 0) {
						return byLoad;
					}
					int byGpu = Double.compare(gpuUtil(a), gpuUtil(b));
					if (byGpu != 0) {
						return byGpu;
					}
					return Long.compare(a.getId(), b.getId());
				})
				.orElse(null);
	}

	private long inFlight(CoreInstance instance) {
		long count = minibatchRepository.countByCoreInstanceIdAndStatus(
				instance.getId(), GenerationJobMinibatch.STATUS_RUNNING);
		if (instance.getCurrentRunId() != null && !instance.getCurrentRunId().isBlank()
				&& minibatchRepository.findByRunId(instance.getCurrentRunId()).isEmpty()) {
			count += 1;
		}
		return count;
	}

	private double gpuUtil(CoreInstance instance) {
		Double value = null;
		if (instance.getLastMetricsJson() != null) {
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> metrics = objectMapper.readValue(instance.getLastMetricsJson(), Map.class);
				value = CoreLoadPolicy.maxGpu(metrics, "util_percent");
			} catch (JsonProcessingException ignored) {
				value = null;
			}
		}
		return value == null ? Double.POSITIVE_INFINITY : value;
	}

	private String writeJson(Map<String, Object> metrics) {
		try {
			return objectMapper.writeValueAsString(metrics);
		} catch (JsonProcessingException e) {
			return null;
		}
	}
}
