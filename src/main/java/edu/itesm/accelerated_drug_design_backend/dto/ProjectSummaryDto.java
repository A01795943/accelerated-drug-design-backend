package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * DTO para listado de proyectos: solo id, nombre y descripción.
 */
public record ProjectSummaryDto(Long id, String name, String description) {}
