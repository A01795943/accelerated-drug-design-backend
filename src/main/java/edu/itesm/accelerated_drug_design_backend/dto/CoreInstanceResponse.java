package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;
import java.util.List;

public record CoreInstanceResponse(
		Long id,
		String name,
		String baseUrl,
		boolean enabled,
		boolean restrictSchedule,
		Integer maxJobMinutes,
		Integer connectTimeoutMs,
		Integer readTimeoutMs,
		String currentRunId,
		String currentRunKind,
		Instant claimedAt,
		String healthState,
		Instant lastHealthAt,
		Instant createdAt,
		Instant updatedAt,
		List<CoreInstanceWindowResponse> windows
) {}
