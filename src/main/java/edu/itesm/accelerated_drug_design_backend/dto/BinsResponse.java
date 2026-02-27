package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

/**
 * Respuesta para el endpoint de análisis por bins.
 * Conteos y porcentajes por categoría, en el mismo orden que los labels.
 */
public record BinsResponse(
	String columnName,
	List<String> labels,
	List<Long> counts,
	List<Double> percentages
) {}
