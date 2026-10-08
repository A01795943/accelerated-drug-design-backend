package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record GenerationJobMinibatchDto(
		Long id,
		Long generationJobId,
		int idx,
		int nOffset,
		int numSeqs,
		String runId,
		Long coreInstanceId,
		String status,
		int attempts,
		String error,
		Instant startedAt,
		Instant completedAt
) {}
