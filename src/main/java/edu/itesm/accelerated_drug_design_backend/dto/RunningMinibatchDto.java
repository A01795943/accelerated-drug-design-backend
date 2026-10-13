package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record RunningMinibatchDto(
		Long generationJobId,
		Long minibatchId,
		int idx,
		int nOffset,
		int numSeqs,
		Double temperature,
		Long backboneId,
		String backboneName,
		Long projectId,
		Instant startedAt) {
}
