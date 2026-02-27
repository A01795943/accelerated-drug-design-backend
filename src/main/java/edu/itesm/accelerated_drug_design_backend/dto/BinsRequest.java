package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request para el endpoint de análisis por bins (categorías).
 * bins: bordes de los intervalos, p.ej. [0, 50, 90, 100].
 * labels: etiquetas por categoría; debe tener longitud len(bins) - 1.
 */
public record BinsRequest(
	@NotNull(message = "bins is required")
	@NotEmpty(message = "bins must not be empty")
	List<Double> bins,

	@NotNull(message = "labels is required")
	@NotEmpty(message = "labels must not be empty")
	@Size(min = 1)
	List<String> labels
) {}
