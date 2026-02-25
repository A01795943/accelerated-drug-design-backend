package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "Current password is required") String currentPassword,
		@NotBlank(message = "New password is required")
		@Size(min = 4, message = "New password must be at least 4 characters")
		String newPassword
) {}
