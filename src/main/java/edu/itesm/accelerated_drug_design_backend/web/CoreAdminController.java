package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CoreAdminInstanceDto;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceMetricsHistoryDto;
import edu.itesm.accelerated_drug_design_backend.dto.PatchCoreAdminRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RegisterCoreRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceMetricsHistoryRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.service.CoreBaseUrlValidator;
import edu.itesm.accelerated_drug_design_backend.service.CoreMetricsPoller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Load-aware admin API. The existing pool CRUD remains at {@code /api/core-instances}
 * (windows, timeouts, delete). This path exposes status, metrics history and refresh.
 */
@RestController
@RequestMapping("/api/cores")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Cores", description = "Core load status, metrics history and registration")
public class CoreAdminController {

	private final CoreInstanceRepository coreInstanceRepository;
	private final GenerationJobMinibatchRepository minibatchRepository;
	private final CoreInstanceMetricsHistoryRepository historyRepository;
	private final CoreBaseUrlValidator baseUrlValidator;
	private final CoreMetricsPoller metricsPoller;

	public CoreAdminController(
			CoreInstanceRepository coreInstanceRepository,
			GenerationJobMinibatchRepository minibatchRepository,
			CoreInstanceMetricsHistoryRepository historyRepository,
			CoreBaseUrlValidator baseUrlValidator,
			CoreMetricsPoller metricsPoller) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.minibatchRepository = minibatchRepository;
		this.historyRepository = historyRepository;
		this.baseUrlValidator = baseUrlValidator;
		this.metricsPoller = metricsPoller;
	}

	@GetMapping
	@Operation(summary = "List core instances with load status and in-flight minibatches")
	public List<CoreAdminInstanceDto> list() {
		return coreInstanceRepository.findAll().stream().map(this::toDto).toList();
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
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(coreInstanceRepository.save(instance)));
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
		return toDto(coreInstanceRepository.save(instance));
	}

	@GetMapping("/{id}/metrics/history")
	@Operation(summary = "Metric samples between from and to (ISO-8601)")
	public List<CoreInstanceMetricsHistoryDto> history(
			@PathVariable Long id,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		if (!coreInstanceRepository.existsById(id)) {
			throw new EntityNotFoundException("Core instance not found: " + id);
		}
		Instant end = to != null ? to : Instant.now();
		Instant start = from != null ? from : end.minusSeconds(7L * 24 * 3600);
		return historyRepository.findByCoreInstanceIdAndCollectedAtBetweenOrderByCollectedAtAsc(id, start, end)
				.stream()
				.map(this::toHistory)
				.toList();
	}

	@PostMapping("/{id}/refresh")
	@Operation(summary = "Query GET /metrics/system now")
	public CoreAdminInstanceDto refresh(@PathVariable Long id) {
		metricsPoller.refresh(id);
		return toDto(coreInstanceRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Core instance not found: " + id)));
	}

	private CoreAdminInstanceDto toDto(CoreInstance instance) {
		long inFlight = minibatchRepository.countByCoreInstanceIdAndStatus(
				instance.getId(), GenerationJobMinibatch.STATUS_RUNNING);
		return new CoreAdminInstanceDto(
				instance.getId(),
				instance.getName(),
				instance.getBaseUrl(),
				instance.isEnabled(),
				instance.getStatus(),
				instance.getStatusReason(),
				instance.getMaxConcurrentRuns(),
				inFlight,
				instance.getHealthState(),
				instance.getCurrentRunId(),
				instance.getLastMetricsAt(),
				instance.getLastMetricsJson());
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
				row.getNetRecvPerSec());
	}
}
