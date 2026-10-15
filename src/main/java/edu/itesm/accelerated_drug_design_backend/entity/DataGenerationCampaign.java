package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "data_generation_campaigns")
public class DataGenerationCampaign {

	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_ERROR = "ERROR";
	public static final String STATUS_PARTIAL = "PARTIAL";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private Project project;

	@Column(name = "status", length = 32, nullable = false)
	private String status = STATUS_RUNNING;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "num_seqs_per_temperature")
	private Integer numSeqsPerTemperature;

	@Column(name = "temperatures_json", columnDefinition = "TEXT")
	private String temperaturesJson;

	@Column(name = "backbone_configs_json", columnDefinition = "TEXT")
	private String backboneConfigsJson;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	@OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<CampaignBackbone> backbones = new ArrayList<>();

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

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Integer getNumSeqsPerTemperature() {
		return numSeqsPerTemperature;
	}

	public void setNumSeqsPerTemperature(Integer numSeqsPerTemperature) {
		this.numSeqsPerTemperature = numSeqsPerTemperature;
	}

	public String getTemperaturesJson() {
		return temperaturesJson;
	}

	public void setTemperaturesJson(String temperaturesJson) {
		this.temperaturesJson = temperaturesJson;
	}

	public String getBackboneConfigsJson() {
		return backboneConfigsJson;
	}

	public void setBackboneConfigsJson(String backboneConfigsJson) {
		this.backboneConfigsJson = backboneConfigsJson;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public List<CampaignBackbone> getBackbones() {
		return backbones;
	}

	public void setBackbones(List<CampaignBackbone> backbones) {
		this.backbones = backbones;
	}
}
