package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PatchCoreAdminRequest(
		Boolean enabled,
		@Min(1) Integer maxConcurrentRuns,
		@Size(max = 255) String baseUrl
) {}
