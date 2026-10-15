package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaign_backbones")
public class CampaignBackbone {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_ERROR = "ERROR";
	public static final String STATUS_SKIPPED = "SKIPPED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "campaign_id", nullable = false)
	@JsonIgnore
	private DataGenerationCampaign campaign;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "config_index", nullable = false)
	private Integer configIndex = 0;

	@Column(columnDefinition = "TEXT")
	private String contigs;

	@Column(columnDefinition = "TEXT")
	private String hotspots;

	@Column(name = "chains_to_remove", columnDefinition = "TEXT")
	private String chainsToRemove;

	@Column(name = "iterations")
	private Integer iterations = 30;

	@Column(name = "status", length = 32, nullable = false)
	private String status = STATUS_PENDING;

	@Column(name = "backbone_id")
	private Long backboneId;

	@Column(name = "run_id", length = 64)
	private String runId;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@OneToMany(mappedBy = "campaignBackbone", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<CampaignTemperatureJob> temperatureJobs = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public DataGenerationCampaign getCampaign() {
		return campaign;
	}

	public void setCampaign(DataGenerationCampaign campaign) {
		this.campaign = campaign;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Integer getConfigIndex() {
		return configIndex;
	}

	public void setConfigIndex(Integer configIndex) {
		this.configIndex = configIndex;
	}

	public String getContigs() {
		return contigs;
	}

	public void setContigs(String contigs) {
		this.contigs = contigs;
	}

	public String getHotspots() {
		return hotspots;
	}

	public void setHotspots(String hotspots) {
		this.hotspots = hotspots;
	}

	public String getChainsToRemove() {
		return chainsToRemove;
	}

	public void setChainsToRemove(String chainsToRemove) {
		this.chainsToRemove = chainsToRemove;
	}

	public Integer getIterations() {
		return iterations;
	}

	public void setIterations(Integer iterations) {
		this.iterations = iterations;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getBackboneId() {
		return backboneId;
	}

	public void setBackboneId(Long backboneId) {
		this.backboneId = backboneId;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
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

	public List<CampaignTemperatureJob> getTemperatureJobs() {
		return temperatureJobs;
	}

	public void setTemperatureJobs(List<CampaignTemperatureJob> temperatureJobs) {
		this.temperatureJobs = temperatureJobs;
	}
}
