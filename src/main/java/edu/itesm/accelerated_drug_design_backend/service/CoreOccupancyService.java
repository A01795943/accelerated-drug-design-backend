package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.OtherRunDto;
import edu.itesm.accelerated_drug_design_backend.dto.RunningMinibatchDto;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.RunningOccupancyRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single in-flight count for the dispatcher and for {@code GET /api/cores}.
 * A running minibatch counts as one. {@code current_run_id} counts as one more
 * only when it is not the run id of a minibatch (a backbone or a job claimed
 * without minibatches). That is the same rule {@code pickEligible} already used.
 */
@Service
public class CoreOccupancyService {

	public static final String FREE = "FREE";
	public static final String IN_USE = "IN_USE";
	public static final String FULL = "FULL";

	private final GenerationJobMinibatchRepository minibatchRepository;

	public CoreOccupancyService(GenerationJobMinibatchRepository minibatchRepository) {
		this.minibatchRepository = minibatchRepository;
	}

	@Transactional(readOnly = true)
	public Map<Long, InstanceOccupancy> byInstance(Collection<CoreInstance> instances) {
		if (instances == null || instances.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = instances.stream().map(CoreInstance::getId).filter(Objects::nonNull).toList();
		Map<Long, List<RunningMinibatchDto>> byInstance = new HashMap<>();
		if (!ids.isEmpty()) {
			for (RunningOccupancyRow row : minibatchRepository.findRunningOccupancy(ids)) {
				byInstance.computeIfAbsent(row.getInstanceId(), ignored -> new ArrayList<>())
						.add(toMinibatch(row));
			}
		}
		Set<String> claimed = instances.stream()
				.map(CoreInstance::getCurrentRunId)
				.filter(this::present)
				.collect(Collectors.toSet());
		Set<String> minibatchRunIds = claimed.isEmpty()
				? Set.of()
				: minibatchRepository.findByRunIdIn(claimed).stream()
						.map(GenerationJobMinibatch::getRunId)
						.collect(Collectors.toSet());

		Map<Long, InstanceOccupancy> result = new LinkedHashMap<>();
		for (CoreInstance instance : instances) {
			List<RunningMinibatchDto> running = byInstance.getOrDefault(instance.getId(), List.of());
			OtherRunDto other = otherRun(instance, minibatchRunIds);
			int max = Math.max(1, instance.getMaxConcurrentRuns());
			long total = running.size() + (other == null ? 0 : 1);
			result.put(instance.getId(), new InstanceOccupancy(
					instance.getId(),
					List.copyOf(running),
					other,
					total,
					max,
					state(total, max)));
		}
		return result;
	}

	public static String state(long inFlightTotal, int maxConcurrentRuns) {
		if (inFlightTotal >= maxConcurrentRuns) {
			return FULL;
		}
		if (inFlightTotal > 0) {
			return IN_USE;
		}
		return FREE;
	}

	private OtherRunDto otherRun(CoreInstance instance, Set<String> minibatchRunIds) {
		String runId = instance.getCurrentRunId();
		if (!present(runId) || minibatchRunIds.contains(runId)) {
			return null;
		}
		String kind = present(instance.getCurrentRunKind()) ? instance.getCurrentRunKind() : "backbone";
		return new OtherRunDto(kind, runId);
	}

	private static RunningMinibatchDto toMinibatch(RunningOccupancyRow row) {
		return new RunningMinibatchDto(
				row.getGenerationJobId(),
				row.getMinibatchId(),
				row.getIdx() == null ? 0 : row.getIdx(),
				row.getNOffset() == null ? 0 : row.getNOffset(),
				row.getNumSeqs() == null ? 0 : row.getNumSeqs(),
				row.getTemperature(),
				row.getBackboneId(),
				row.getBackboneName(),
				row.getProjectId(),
				row.getStartedAt());
	}

	private boolean present(String value) {
		return value != null && !value.isBlank();
	}

	public record InstanceOccupancy(
			Long instanceId,
			List<RunningMinibatchDto> runningMinibatches,
			OtherRunDto otherRun,
			long inFlightTotal,
			int maxConcurrentRuns,
			String state) {
	}
}
