package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CoreInstancePatchRequest(
		@Size(min = 1, max = 120)
		String name,
		@Size(min = 1, max = 255)
		String baseUrl,
		Boolean enabled,
		Boolean restrictSchedule,
		@Min(0)
		Integer maxJobMinutes,
		@Min(0)
		Integer connectTimeoutMs,
		@Min(0)
		Integer readTimeoutMs,
		@Valid
		List<CoreInstanceWindowRequest> windows
) {}
