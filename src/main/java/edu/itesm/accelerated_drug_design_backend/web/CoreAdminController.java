package edu.itesm.accelerated_drug_design_backend.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.itesm.accelerated_drug_design_backend.config.CoreLoadProperties;
import edu.itesm.accelerated_drug_design_backend.dto.CoreAdminInstanceDto;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceMetricsHistoryDto;
import edu.itesm.accelerated_drug_design_backend.dto.GpuReading;
import edu.itesm.accelerated_drug_design_backend.dto.LoadPolicyDto;
import edu.itesm.accelerated_drug_design_backend.dto.PatchCoreAdminRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RegisterCoreRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceMetricsHistoryRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.service.CoreBaseUrlValidator;
import edu.itesm.accelerated_drug_design_backend.service.CoreMetricsPoller;
import edu.itesm.accelerated_drug_design_backend.service.CoreOccupancyService;
import edu.itesm.accelerated_drug_design_backend.service.GpuMetrics;
import edu.itesm.accelerated_drug_design_backend.service.MetricsHistorySampler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Load-aware admin API. The existing pool CRUD remains at {@code /api/core-instances}
 * (windows, timeouts, delete). This path exposes status, metrics history and refresh.
 */
@RestController
@RequestMapping("/api/cores")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Cores", description = "Core load status, metrics history and registration")
public class CoreAdminController {

	private static final Set<Integer> HISTORY_HOURS = Set.of(1, 6, 24, 168);

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreInstanceMetricsHistoryRepository historyRepository;
	private final CoreBaseUrlValidator baseUrlValidator;
	private final CoreMetricsPoller metricsPoller;
	private final CoreOccupancyService occupancyService;
	private final CoreLoadProperties loadProperties;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public CoreAdminController(
			CoreInstanceRepository coreInstanceRepository,
			CoreInstanceMetricsHistoryRepository historyRepository,
			CoreBaseUrlValidator baseUrlValidator,
			CoreMetricsPoller metricsPoller,
			CoreOccupancyService occupancyService,
			CoreLoadProperties loadProperties) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.historyRepository = historyRepository;
		this.baseUrlValidator = baseUrlValidator;
		this.metricsPoller = metricsPoller;
		this.occupancyService = occupancyService;
		this.loadProperties = loadProperties;
	}

	@GetMapping
	@Operation(summary = "List core instances with the same occupancy the dispatcher uses")
	public List<CoreAdminInstanceDto> list() {
		List<CoreInstance> instances = coreInstanceRepository.findAll();
		Map<Long, CoreOccupancyService.InstanceOccupancy> loads = occupancyService.byInstance(instances);
		return instances.stream().map(instance -> toDto(instance, loads.get(instance.getId()))).toList();
	}

	@GetMapping("/load-policy")
	@Operation(summary = "Load thresholds used to color meters and to mark a core OVERLOADED")
	public LoadPolicyDto loadPolicy() {
		CoreLoadPolicyThresholds enter = new CoreLoadPolicyThresholds(loadProperties.enter());
		CoreLoadPolicyThresholds recover = new CoreLoadPolicyThresholds(loadProperties.recover());
		return new LoadPolicyDto(
				enter.cpu, enter.memory, enter.gpuUtil, enter.gpuMem, enter.disk,
				recover.cpu, recover.memory, recover.gpuUtil, recover.gpuMem, recover.disk);
	}

	@PostMapping
	@Operation(summary = "Register a core instance")
	public ResponseEntity<CoreAdminInstanceDto> create(@Valid @RequestBody RegisterCoreRequest request) {
		String baseUrl = baseUrlValidator.validateAndNormalize(request.baseUrl());
		if (coreInstanceRepository.existsByBaseUrl(baseUrl)) {
			throw new IllegalArgumentException("baseUrl already exists");
		}
		CoreInstance instance = new CoreInstance();
		instance.setName(request.name().trim());
		instance.setBaseUrl(baseUrl);
		instance.setEnabled(true);
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		instance.setStatus(CoreInstance.STATUS_AVAILABLE);
		instance.setMaxConcurrentRuns(request.maxConcurrentRuns() == null ? 1 : request.maxConcurrentRuns());
		CoreInstance saved = coreInstanceRepository.save(instance);
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved, occupancyService.byInstance(List.of(saved)).get(saved.getId())));
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Update enabled, maxConcurrentRuns or baseUrl")
	public CoreAdminInstanceDto patch(@PathVariable Long id, @Valid @RequestBody PatchCoreAdminRequest request) {
		CoreInstance instance = coreInstanceRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Core instance not found: " + id));
		if (request.enabled() != null) {
			instance.setEnabled(request.enabled());
		}
		if (request.maxConcurrentRuns() != null) {
			instance.setMaxConcurrentRuns(request.maxConcurrentRuns());
		}
		if (request.baseUrl() != null) {
			String baseUrl = baseUrlValidator.validateAndNormalize(request.baseUrl());
			if (coreInstanceRepository.existsByBaseUrlAndIdNot(baseUrl, id)) {
				throw new IllegalArgumentException("baseUrl already exists");
			}
			instance.setBaseUrl(baseUrl);
		}
		CoreInstance saved = coreInstanceRepository.save(instance);
		return toDto(saved, occupancyService.byInstance(List.of(saved)).get(saved.getId()));
	}

	@GetMapping("/{id}/metrics/history")
	@Operation(summary = "Metric samples. hours is 1, 6, 24 or 168. from and to stay available.")
	public List<CoreInstanceMetricsHistoryDto> history(
			@PathVariable Long id,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) Integer hours) {
		if (!coreInstanceRepository.existsById(id)) {
			throw new EntityNotFoundException("Core instance not found: " + id);
		}
		Instant end;
		Instant start;
		if (hours != null) {
			if (!HISTORY_HOURS.contains(hours)) {
				throw new IllegalArgumentException("hours must be 1, 6, 24 or 168");
			}
			end = Instant.now();
			start = end.minus(hours, ChronoUnit.HOURS);
		} else {
			end = to != null ? to : Instant.now();
			start = from != null ? from : end.minus(7, ChronoUnit.DAYS);
		}
		List<CoreInstanceMetricsHistoryDto> points = historyRepository
				.findByCoreInstanceIdAndCollectedAtBetweenOrderByCollectedAtAsc(id, start, end)
				.stream()
				.map(this::toHistory)
				.toList();
		return MetricsHistorySampler.limit(points);
	}

	@PostMapping("/{id}/refresh")
	@Operation(summary = "Query GET /metrics/system now")
	public CoreAdminInstanceDto refresh(@PathVariable Long id) {
		metricsPoller.refresh(id);
		CoreInstance instance = coreInstanceRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Core instance not found: " + id));
		return toDto(instance, occupancyService.byInstance(List.of(instance)).get(instance.getId()));
	}

	private CoreAdminInstanceDto toDto(CoreInstance instance, CoreOccupancyService.InstanceOccupancy load) {
		List<GpuReading> gpus = List.of();
		boolean gpuData = false;
		Map<String, Object> metrics = parseMetrics(instance.getLastMetricsJson());
		if (metrics != null) {
			Object node = metrics.get("gpus");
			gpuData = GpuMetrics.hasData(node);
			gpus = GpuMetrics.read(node);
		}
		long minibatchCount = load == null ? 0 : load.runningMinibatches().size();
		return new CoreAdminInstanceDto(
				instance.getId(),
				instance.getName(),
				instance.getBaseUrl(),
				instance.isEnabled(),
				instance.getStatus(),
				instance.getStatusReason(),
				instance.getMaxConcurrentRuns(),
				minibatchCount,
				instance.getHealthState(),
				instance.getCurrentRunId(),
				instance.getLastMetricsAt(),
				instance.getLastMetricsJson(),
				load == null ? 0 : load.inFlightTotal(),
				load == null ? CoreOccupancyService.FREE : load.state(),
				load == null ? List.of() : load.runningMinibatches(),
				load == null ? null : load.otherRun(),
				gpuData,
				gpus);
	}

	private CoreInstanceMetricsHistoryDto toHistory(CoreInstanceMetricsHistory row) {
		return new CoreInstanceMetricsHistoryDto(
				row.getId(),
				row.getCoreInstanceId(),
				row.getCollectedAt(),
				row.getCpuPercent(),
				row.getMemPercent(),
				row.getGpuUtilPercent(),
				row.getGpuMemPercent(),
				row.getDiskPercent(),
				row.getRunningCount(),
				row.getNetSentPerSec(),
				row.getNetRecvPerSec(),
				row.getGpuMemUsedMb(),
				row.getGpuMemTotalMb());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseMetrics(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, Map.class);
		} catch (JsonProcessingException ex) {
			return null;
		}
	}

	private record CoreLoadPolicyThresholds(double cpu, double memory, double gpuUtil, double gpuMem, double disk) {
		private CoreLoadPolicyThresholds(edu.itesm.accelerated_drug_design_backend.service.CoreLoadPolicy.Thresholds source) {
			this(source.cpu(), source.memory(), source.gpuUtil(), source.gpuMem(), source.disk());
		}
	}
}
