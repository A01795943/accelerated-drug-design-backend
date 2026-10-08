package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCoreRequest(
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Size(max = 255) String baseUrl,
		@Min(1) Integer maxConcurrentRuns
) {}
