package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreateGenerationJobRequest {

	@NotNull(message = "backboneId is required")
	private Long backboneId;

	@DecimalMin(value = "0.0", message = "temperature must be at least 0")
	@DecimalMax(value = "1.0", message = "temperature must be at most 1")
	private Double temperature = 0.1;

	@NotNull(message = "numSeqs is required")
	@Min(value = 1, message = "numSeqs must be at least 1")
	private Integer numSeqs = 16;

	public Long getBackboneId() {
		return backboneId;
	}

	public void setBackboneId(Long backboneId) {
		this.backboneId = backboneId;
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public Integer getNumSeqs() {
		return numSeqs;
	}

	public void setNumSeqs(Integer numSeqs) {
		this.numSeqs = numSeqs;
	}
}
