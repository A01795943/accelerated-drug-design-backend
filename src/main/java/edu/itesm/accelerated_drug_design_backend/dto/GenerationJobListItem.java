package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DTO for generation job list API. Excludes outputCsv, fasta and bestPdb so the list query
 * does not load those LONGTEXT columns. Includes backboneId and backboneName for the frontend.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationJobListItem {

	private Long id;
	private String runId;
	private String status;
	private String error;
	private Double temperature;
	private Integer numSeqs;
	private Integer totalRecords;
	private Long backboneId;
	private String backboneName;
	private Instant createdAt;
	private Instant completedAt;
	/** Global dataset quality 0–1 (from EDA), null if not computed or not applicable. */
	private Double quality;
	/** Max pTM across records for this job. */
	private Double maxPtm;
	/** Max iPTM across records for this job. */
	private Double maxIPtm;

	/** For JPQL constructor query: only list fields (no outputCsv, fasta, bestPdb). */
	public GenerationJobListItem(Long id, String runId, String status, String error,
			Double temperature, Integer numSeqs, Integer totalRecords,
			Long backboneId, String backboneName, Instant createdAt, Instant completedAt) {
		this.id = id;
		this.runId = runId;
		this.status = status;
		this.error = error;
		this.temperature = temperature;
		this.numSeqs = numSeqs;
		this.totalRecords = totalRecords;
		this.backboneId = backboneId;
		this.backboneName = backboneName;
		this.createdAt = createdAt;
		this.completedAt = completedAt;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getRunId() { return runId; }
	public void setRunId(String runId) { this.runId = runId; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getError() { return error; }
	public void setError(String error) { this.error = error; }
	public Double getTemperature() { return temperature; }
	public void setTemperature(Double temperature) { this.temperature = temperature; }
	public Integer getNumSeqs() { return numSeqs; }
	public void setNumSeqs(Integer numSeqs) { this.numSeqs = numSeqs; }
	public Integer getTotalRecords() { return totalRecords; }
	public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
	public Long getBackboneId() { return backboneId; }
	public void setBackboneId(Long backboneId) { this.backboneId = backboneId; }
	public String getBackboneName() { return backboneName; }
	public void setBackboneName(String backboneName) { this.backboneName = backboneName; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
	public Double getQuality() { return quality; }
	public void setQuality(Double quality) { this.quality = quality; }
	public Double getMaxPtm() { return maxPtm; }
	public void setMaxPtm(Double maxPtm) { this.maxPtm = maxPtm; }
	public Double getMaxIPtm() { return maxIPtm; }
	public void setMaxIPtm(Double maxIPtm) { this.maxIPtm = maxIPtm; }
}
