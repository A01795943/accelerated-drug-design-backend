package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CoreInstancePatchRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceResponse;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceWindowRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceWindowResponse;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceWindow;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import edu.itesm.accelerated_drug_design_backend.web.ResourceConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CoreInstanceService {

	private final CoreInstanceRepository coreInstanceRepository;
	private final BackboneRepository backboneRepository;
	private final GenerationJobRepository generationJobRepository;
	private final CoreBaseUrlValidator baseUrlValidator;
	private final CoreOccupancyService occupancyService;
	private final CoreMetricsPoller metricsPoller;

	public CoreInstanceService(
			CoreInstanceRepository coreInstanceRepository,
			BackboneRepository backboneRepository,
			GenerationJobRepository generationJobRepository,
			CoreBaseUrlValidator baseUrlValidator,
			CoreOccupancyService occupancyService,
			CoreMetricsPoller metricsPoller) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.backboneRepository = backboneRepository;
		this.generationJobRepository = generationJobRepository;
		this.baseUrlValidator = baseUrlValidator;
		this.occupancyService = occupancyService;
		this.metricsPoller = metricsPoller;
	}

	public boolean isEligible(CoreInstance instance) {
		return metricsPoller.isEligible(instance);
	}

	@Transactional(readOnly = true)
	public List<CoreInstanceResponse> list() {
		return coreInstanceRepository.findAllWithWindows().stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public CoreInstanceResponse get(Long id) {
		return toResponse(requireWithWindows(id));
	}

	@Transactional
	public CoreInstanceResponse create(CoreInstanceRequest request) {
		String baseUrl = baseUrlValidator.validateAndNormalize(request.baseUrl());
		assertBaseUrlAvailable(baseUrl, null);

		CoreInstance instance = new CoreInstance();
		instance.setName(request.name().trim());
		instance.setBaseUrl(baseUrl);
		instance.setEnabled(request.enabled() == null || request.enabled());
		instance.setRestrictSchedule(Boolean.TRUE.equals(request.restrictSchedule()));
		instance.setMaxJobMinutes(request.maxJobMinutes());
		instance.setConnectTimeoutMs(request.connectTimeoutMs());
		instance.setReadTimeoutMs(request.readTimeoutMs());
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		replaceWindows(instance, request.windows());
		return toResponse(coreInstanceRepository.save(instance));
	}

	@Transactional
	public CoreInstanceResponse patch(Long id, CoreInstancePatchRequest request) {
		CoreInstance instance = requireWithWindows(id);
		if (request.name() != null) {
			instance.setName(request.name().trim());
		}
		if (request.baseUrl() != null) {
			String baseUrl = baseUrlValidator.validateAndNormalize(request.baseUrl());
			assertBaseUrlAvailable(baseUrl, id);
			instance.setBaseUrl(baseUrl);
		}
		if (request.enabled() != null) {
			instance.setEnabled(request.enabled());
		}
		if (request.restrictSchedule() != null) {
			instance.setRestrictSchedule(request.restrictSchedule());
		}
		if (request.maxJobMinutes() != null) {
			instance.setMaxJobMinutes(request.maxJobMinutes());
		}
		if (request.connectTimeoutMs() != null) {
			instance.setConnectTimeoutMs(request.connectTimeoutMs());
		}
		if (request.readTimeoutMs() != null) {
			instance.setReadTimeoutMs(request.readTimeoutMs());
		}
		if (request.windows() != null) {
			replaceWindows(instance, request.windows());
		}
		return toResponse(coreInstanceRepository.save(instance));
	}

	@Transactional
	public void delete(Long id) {
		CoreInstance instance = coreInstanceRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Core instance not found: " + id));
		CoreOccupancyService.InstanceOccupancy load = occupancyService.byInstance(List.of(instance)).get(id);
		if (load != null && load.inFlightTotal() > 0) {
			throw new ResourceConflictException(
					"Cannot delete instance while runs are in flight (" + describe(load)
							+ "). Disable it (enabled=false) instead of deleting. Disabling does not stop a run that is already going.");
		}
		if (backboneRepository.existsByCoreInstanceId(id)
				|| generationJobRepository.existsByCoreInstanceId(id)) {
			throw new ResourceConflictException(
					"Cannot delete instance because backbones or generation jobs still reference it. "
							+ "Disable it (enabled=false) instead of deleting.");
		}
		coreInstanceRepository.delete(instance);
	}

	private static String describe(CoreOccupancyService.InstanceOccupancy load) {
		StringBuilder text = new StringBuilder();
		for (edu.itesm.accelerated_drug_design_backend.dto.RunningMinibatchDto minibatch : load.runningMinibatches()) {
			if (!text.isEmpty()) {
				text.append("; ");
			}
			text.append("job ").append(minibatch.generationJobId())
					.append(" minibatch ").append(minibatch.idx());
			if (minibatch.backboneName() != null) {
				text.append(" backbone ").append(minibatch.backboneName());
			}
		}
		if (load.otherRun() != null) {
			if (!text.isEmpty()) {
				text.append("; ");
			}
			text.append(load.otherRun().kind()).append(' ').append(load.otherRun().runId());
		}
		return text.toString();
	}

	private CoreInstance requireWithWindows(Long id) {
		return coreInstanceRepository.findWithWindowsById(id)
				.orElseThrow(() -> new EntityNotFoundException("Core instance not found: " + id));
	}

	private void assertBaseUrlAvailable(String baseUrl, Long currentId) {
		boolean taken = currentId == null
				? coreInstanceRepository.existsByBaseUrl(baseUrl)
				: coreInstanceRepository.existsByBaseUrlAndIdNot(baseUrl, currentId);
		if (taken) {
			throw new IllegalArgumentException("baseUrl already exists");
		}
	}

	private void replaceWindows(CoreInstance instance, List<CoreInstanceWindowRequest> windows) {
		instance.getWindows().clear();
		if (windows == null) {
			return;
		}
		for (CoreInstanceWindowRequest dto : windows) {
			CoreInstanceWindow window = new CoreInstanceWindow();
			window.setCoreInstance(instance);
			window.setDayOfWeek(dto.dayOfWeek());
			window.setStartTime(dto.startTime());
			window.setEndTime(dto.endTime());
			instance.getWindows().add(window);
		}
	}

	private CoreInstanceResponse toResponse(CoreInstance instance) {
		List<CoreInstanceWindowResponse> windows = instance.getWindows().stream()
				.sorted(Comparator
						.comparing(CoreInstanceWindow::getDayOfWeek, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(CoreInstanceWindow::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
				.map(w -> new CoreInstanceWindowResponse(w.getId(), w.getDayOfWeek(), w.getStartTime(), w.getEndTime()))
				.toList();
		return new CoreInstanceResponse(
				instance.getId(),
				instance.getName(),
				instance.getBaseUrl(),
				instance.isEnabled(),
				instance.isRestrictSchedule(),
				instance.getMaxJobMinutes(),
				instance.getConnectTimeoutMs(),
				instance.getReadTimeoutMs(),
				instance.getCurrentRunId(),
				instance.getCurrentRunKind(),
				instance.getClaimedAt(),
				instance.getHealthState(),
				instance.getLastHealthAt(),
				instance.getCreatedAt(),
				instance.getUpdatedAt(),
				windows);
	}
}
