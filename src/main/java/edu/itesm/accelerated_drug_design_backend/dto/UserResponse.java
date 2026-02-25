package edu.itesm.accelerated_drug_design_backend.dto;

public record UserResponse(Long id, String username, String email, String role, boolean enabled) {}
