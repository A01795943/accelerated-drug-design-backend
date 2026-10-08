package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record CoreAdminInstanceDto(
		Long id,
		String name,
		String baseUrl,
		boolean enabled,
		String status,
		String statusReason,
		int maxConcurrentRuns,
		long inFlightMinibatches,
		String healthState,
		String currentRunId,
		Instant lastMetricsAt,
		String lastMetricsJson
) {}
