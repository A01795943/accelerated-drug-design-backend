package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DTO for generation job list API. Includes backboneId and backboneName so the frontend
 * can show backbone as "#id name" without relying on entity lazy loading during JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationJobListItem {

	private Long id;
	private String runId;
	private String status;
	private String error;
	private Double temperature;
	private Integer numSeqs;
	private String outputCsv;
	private String fasta;
	private String bestPdb;
	private Integer totalRecords;
	private Long backboneId;
	private String backboneName;
	private Instant createdAt;
	private Instant completedAt;

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
	public String getOutputCsv() { return outputCsv; }
	public void setOutputCsv(String outputCsv) { this.outputCsv = outputCsv; }
	public String getFasta() { return fasta; }
	public void setFasta(String fasta) { this.fasta = fasta; }
	public String getBestPdb() { return bestPdb; }
	public void setBestPdb(String bestPdb) { this.bestPdb = bestPdb; }
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
}
