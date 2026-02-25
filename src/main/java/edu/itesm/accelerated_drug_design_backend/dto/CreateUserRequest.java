package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank(message = "Username is required")
		@Size(min = 2, max = 100)
		String username,
		@NotBlank(message = "Email is required")
		@Email
		String email,
		@NotBlank(message = "Password is required")
		@Size(min = 4, message = "Password must be at least 4 characters")
		String password,
		@NotBlank(message = "Role is required")
		@Pattern(regexp = "USER|ADMIN", message = "Role must be USER or ADMIN")
		String role
) {}
