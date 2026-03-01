package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Estadísticas descriptivas para una métrica: mean, std, min, percentiles, max, skew, kurtosis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DescriptiveStatsDto(
	Double mean,
	Double std,
	Double min,
	Double p25,
	Double p50,
	Double p75,
	Double max,
	Double skew,
	Double kurtosis,
	Double quality
) {}
