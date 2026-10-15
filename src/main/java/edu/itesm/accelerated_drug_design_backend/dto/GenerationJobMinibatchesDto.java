package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;
import java.util.List;

public record GenerationJobMinibatchesDto(
		Long jobId,
		String jobStatus,
		Instant jobStartedAt,
		Instant jobCompletedAt,
		Integer completedMinibatches,
		Integer totalMinibatches,
		List<MinibatchDto> minibatches
) {
	public record InstanceRef(Long id, String name, String baseUrl) {}

	public record AttemptDto(
			int attempt,
			InstanceRef instance,
			Instant startedAt,
			Instant finishedAt,
			Long durationMillis,
			String outcome,
			String error
	) {}

	public record MinibatchDto(
			int idx,
			int nOffset,
			int sequenceEnd,
			int numSeqs,
			String status,
			String runId,
			InstanceRef instance,
			int attempts,
			String error,
			Instant startedAt,
			Instant completedAt,
			Long durationMillis,
			List<AttemptDto> attemptHistory
	) {}
}
