package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;
import java.util.List;

public class CampaignPhysicalBackboneDto {

	private Long id;
	private String runId;
	private String status;
	private Instant createdAt;
	private Instant updatedAt;
	private Long backboneId;
	private String error;
	private List<CampaignTemperatureJobProgressDto> temperatureJobs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getBackboneId() {
		return backboneId;
	}

	public void setBackboneId(Long backboneId) {
		this.backboneId = backboneId;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public List<CampaignTemperatureJobProgressDto> getTemperatureJobs() {
		return temperatureJobs;
	}

	public void setTemperatureJobs(List<CampaignTemperatureJobProgressDto> temperatureJobs) {
		this.temperatureJobs = temperatureJobs;
	}
}
