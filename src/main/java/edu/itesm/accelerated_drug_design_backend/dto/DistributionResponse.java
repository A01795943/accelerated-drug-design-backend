package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Respuesta para el endpoint de distribución (histograma).
 * Incluye los valores de la columna, mínimo y máximo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DistributionResponse(
	String columnName,
	List<Double> values,
	Double min,
	Double max
) {}
