package edu.itesm.accelerated_drug_design_backend.dto;

public record LoginResponse(String token, Long id, String username, String email, String role) {}
