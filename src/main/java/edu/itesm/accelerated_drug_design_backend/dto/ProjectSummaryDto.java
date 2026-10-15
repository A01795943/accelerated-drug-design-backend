package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * Resumen de proyecto para listado y detalle. No incluye el texto PDB ni el linaje EC.
 */
public record ProjectSummaryDto(Long id, String name, String description, String targetPdbId, String ecNumber) {}
