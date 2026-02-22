package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id", length = 64, unique = true)
	private String runId;

	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "backbone_id", nullable = false)
	@JsonIgnore
	private Backbone backbone;

	@Column(name = "status", length = 32)
	private String status;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	private Double temperature;

	@Column(name = "num_seqs")
	private Integer numSeqs;

	@Column(name = "output_csv", columnDefinition = "LONGTEXT")
	private String outputCsv;

	@Column(columnDefinition = "LONGTEXT")
	private String fasta;

	@Column(name = "best_pdb", columnDefinition = "LONGTEXT")
	private String bestPdb;

	@Column(name = "total_records")
	private Integer totalRecords;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public Backbone getBackbone() {
		return backbone;
	}

	public void setBackbone(Backbone backbone) {
		this.backbone = backbone;
	}

	/** Exposed in API so the frontend can show backbone as "#id name" without loading the full entity. */
	@JsonProperty("backboneId")
	public Long getBackboneId() {
		return backbone == null ? null : backbone.getId();
	}

	@JsonProperty("backboneName")
	public String getBackboneName() {
		return backbone == null ? null : backbone.getName();
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

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
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

	public String getOutputCsv() {
		return outputCsv;
	}

	public void setOutputCsv(String outputCsv) {
		this.outputCsv = outputCsv;
	}

	public String getFasta() {
		return fasta;
	}

	public void setFasta(String fasta) {
		this.fasta = fasta;
	}

	public String getBestPdb() {
		return bestPdb;
	}

	public void setBestPdb(String bestPdb) {
		this.bestPdb = bestPdb;
	}

	public Integer getTotalRecords() {
		return totalRecords;
	}

	public void setTotalRecords(Integer totalRecords) {
		this.totalRecords = totalRecords;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
