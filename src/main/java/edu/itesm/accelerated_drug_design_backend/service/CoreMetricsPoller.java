package edu.itesm.accelerated_drug_design_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.itesm.accelerated_drug_design_backend.config.CoreLoadProperties;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.GpuReading;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceMetricsHistoryRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@EnableConfigurationProperties(CoreLoadProperties.class)
public class CoreMetricsPoller {

	private static final Logger log = LoggerFactory.getLogger(CoreMetricsPoller.class);
	private static final int METRICS_TIMEOUT_MS = 5_000;

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreInstanceMetricsHistoryRepository historyRepository;
	private final CoreOccupancyService occupancyService;
	private final CoreSystemInterface coreSystem;
	private final CoreLoadProperties loadProperties;
	private final ObjectProvider<CoreMetricsPoller> self;
	private final CoreInstanceWriteGate writeGate;
	private final RepeatedLog repeatedLog = new RepeatedLog(10);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ExecutorService metricsExecutor = Executors.newFixedThreadPool(4);
	private final AtomicBoolean pollRunning = new AtomicBoolean(false);
	private final int maxFailures;
	private final int retentionDays;

	public CoreMetricsPoller(
			CoreInstanceRepository coreInstanceRepository,
			CoreInstanceMetricsHistoryRepository historyRepository,
			CoreOccupancyService occupancyService,
			CoreSystemInterface coreSystem,
			CoreLoadProperties loadProperties,
			CoreInstanceWriteGate writeGate,
			ObjectProvider<CoreMetricsPoller> self,
			@Value("${app.core.metrics.max-failures:3}") int maxFailures,
			@Value("${app.core.metrics.retention-days:7}") int retentionDays) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.historyRepository = historyRepository;
		this.occupancyService = occupancyService;
		this.coreSystem = coreSystem;
		this.loadProperties = loadProperties;
		this.writeGate = writeGate;
		this.self = self;
		this.maxFailures = Math.max(1, maxFailures);
		this.retentionDays = Math.max(1, retentionDays);
	}

	@Scheduled(fixedDelayString = "${app.core.metrics.poll-interval-ms:300000}")
	public void poll() {
		List<CoreInstance> enabled = coreInstanceRepository.findByEnabledTrue();
		if (enabled.isEmpty()) {
			return;
		}
		if (!pollRunning.compareAndSet(false, true)) {
			log.info("Metrics poll still in progress; skipping this cycle");
			return;
		}
		AtomicInteger failed = new AtomicInteger();
		List<CompletableFuture<Void>> tasks = new ArrayList<>();
		for (CoreInstance instance : enabled) {
			tasks.add(CompletableFuture.runAsync(() -> pollIsolated(instance), metricsExecutor)
					.orTimeout(METRICS_TIMEOUT_MS + 1_000L, TimeUnit.MILLISECONDS)
					.exceptionally(error -> {
						failed.incrementAndGet();
						repeatedLog.warn(log, "metrics-" + instance.getId(),
								"Metrics poll failed for instance " + instance.getId() + ": " + error.getMessage(),
								error);
						return null;
					}));
		}
		CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).whenComplete((ok, error) -> {
			pollRunning.set(false);
			log.info("Metrics poll finished: instances={} failures={}", enabled.size(), failed.get());
		});
	}

	@PreDestroy
	void shutdownMetricsExecutor() {
		metricsExecutor.shutdownNow();
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
		Map<String, Object> metrics = coreSystem.getSystemMetrics(snapshot, METRICS_TIMEOUT_MS, METRICS_TIMEOUT_MS);
		repeatedLog.clear("metrics-" + snapshot.getId());
		DbLockRetry.run("apply metrics " + snapshot.getId(), () -> writeGate.withLock(
				snapshot.getId(), () -> self.getObject().apply(snapshot.getId(), metrics)));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void apply(Long instanceId, Map<String, Object> metrics) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId).orElse(null);
		if (instance == null) {
			return;
		}
		if (metrics == null) {
			markUnreachable(instance, "metrics unreachable");
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
		Instant collectedAt = Instant.now();
		coreInstanceRepository.updateLoadSnapshot(
				instance.getId(),
				decision.status(),
				decision.reason(),
				0,
				collectedAt,
				writeJson(metrics));

		CoreInstanceMetricsHistory row = new CoreInstanceMetricsHistory();
		row.setCoreInstanceId(instance.getId());
		row.setCollectedAt(collectedAt);
		row.setCpuPercent(CoreLoadPolicy.nestedPercent(metrics, "cpu"));
		row.setMemPercent(CoreLoadPolicy.nestedPercent(metrics, "memory"));
		row.setGpuUtilPercent(CoreLoadPolicy.maxGpu(metrics, "util_percent"));
		row.setGpuMemPercent(CoreLoadPolicy.maxGpu(metrics, "mem_percent"));
		GpuReading busiest = GpuMetrics.busiestMemory(GpuMetrics.read(metrics.get("gpus")));
		if (busiest != null) {
			row.setGpuMemUsedMb(busiest.memUsedMb());
			row.setGpuMemTotalMb(busiest.memTotalMb());
		}
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

	public void recordUnreachable(Long instanceId, String reason) {
		DbLockRetry.run("record unreachable " + instanceId, () -> writeGate.withLock(
				instanceId, () -> self.getObject().recordUnreachableTx(instanceId, reason)));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void recordUnreachableTx(Long instanceId, String reason) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId).orElse(null);
		if (instance == null) {
			return;
		}
		markUnreachable(instance, reason);
	}

	public void recordReachable(Long instanceId) {
		DbLockRetry.run("record reachable " + instanceId, () -> writeGate.withLock(
				instanceId, () -> self.getObject().recordReachableTx(instanceId)));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void recordReachableTx(Long instanceId) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId).orElse(null);
		if (instance == null) {
			return;
		}
		boolean changed = false;
		if (instance.getConsecutiveFailures() != 0) {
			instance.setConsecutiveFailures(0);
			changed = true;
		}
		if (CoreInstance.STATUS_UNAVAILABLE.equals(instance.getStatus())) {
			log.info("Core instance {} UNAVAILABLE -> AVAILABLE (responded)", instance.getName());
			instance.setStatus(CoreInstance.STATUS_AVAILABLE);
			instance.setStatusReason(null);
			changed = true;
		}
		if (changed) {
			coreInstanceRepository.updateReachability(
					instance.getId(), instance.getStatus(), instance.getStatusReason(), instance.getConsecutiveFailures());
		}
	}

	private void markUnreachable(CoreInstance instance, String reason) {
		int failures = instance.getConsecutiveFailures() + 1;
		String status = instance.getStatus();
		String statusReason = instance.getStatusReason();
		if (failures >= maxFailures) {
			if (!CoreInstance.STATUS_UNAVAILABLE.equals(status)) {
				log.info("Core instance {} {} -> UNAVAILABLE (failures={}, reason={})",
						instance.getName(), status, failures, reason);
			}
			status = CoreInstance.STATUS_UNAVAILABLE;
			statusReason = trimReason(reason);
		}
		coreInstanceRepository.updateReachability(instance.getId(), status, statusReason, failures);
	}

	private static String trimReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return "unreachable";
		}
		String trimmed = reason.strip();
		return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
	}

	@Transactional(readOnly = true)
	public boolean isEligible(CoreInstance instance) {
		if (instance == null) {
			return false;
		}
		CoreOccupancyService.InstanceOccupancy load = occupancyService.byInstance(List.of(instance)).get(instance.getId());
		return ineligibleReason(instance, load) == null;
	}

	@Transactional(readOnly = true)
	public List<InstanceLoad> describeInstances() {
		List<CoreInstance> instances = coreInstanceRepository.findAll();
		Map<Long, CoreOccupancyService.InstanceOccupancy> loads = occupancyService.byInstance(instances);
		List<InstanceLoad> rows = new ArrayList<>();
		for (CoreInstance instance : instances) {
			CoreOccupancyService.InstanceOccupancy load = loads.get(instance.getId());
			rows.add(new InstanceLoad(
					instance.getId(),
					instance.getStatus(),
					instance.isEnabled(),
					load == null ? 0 : load.inFlightTotal(),
					load == null ? Math.max(1, instance.getMaxConcurrentRuns()) : load.maxConcurrentRuns(),
					ineligibleReason(instance, load)));
		}
		return rows;
	}

	private String ineligibleReason(CoreInstance instance, CoreOccupancyService.InstanceOccupancy load) {
		if (instance == null) {
			return "missing";
		}
		if (!instance.isEnabled()) {
			return "disabled";
		}
		if (!CoreInstance.STATUS_AVAILABLE.equals(instance.getStatus())) {
			return "status " + instance.getStatus();
		}
		if (CoreInstance.HEALTH_DOWN.equals(instance.getHealthState())) {
			return "health DOWN";
		}
		long inFlight = load == null ? 0 : load.inFlightTotal();
		int max = load == null ? Math.max(1, instance.getMaxConcurrentRuns()) : load.maxConcurrentRuns();
		if (inFlight >= max) {
			return "at capacity";
		}
		return null;
	}

	public record InstanceLoad(Long id, String status, boolean enabled, long inFlight, int maxConcurrent, String skipReason) {}

	public CoreInstance pickEligible() {
		List<CoreInstance> enabled = coreInstanceRepository.findByEnabledTrue();
		Map<Long, CoreOccupancyService.InstanceOccupancy> loads = occupancyService.byInstance(enabled);
		return enabled.stream()
				.filter(instance -> ineligibleReason(instance, loads.get(instance.getId())) == null)
				.min((a, b) -> {
					long left = loads.get(a.getId()) == null ? 0 : loads.get(a.getId()).inFlightTotal();
					long right = loads.get(b.getId()) == null ? 0 : loads.get(b.getId()).inFlightTotal();
					int byLoad = Long.compare(left, right);
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
