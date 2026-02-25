package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Username is required") String username,
		@NotBlank(message = "Password is required") String password
) {}
