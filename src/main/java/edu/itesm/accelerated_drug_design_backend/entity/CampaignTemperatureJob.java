package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "campaign_temperature_jobs")
public class CampaignTemperatureJob {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_ERROR = "ERROR";
	public static final String STATUS_SKIPPED = "SKIPPED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "campaign_backbone_id", nullable = false)
	@JsonIgnore
	private CampaignBackbone campaignBackbone;

	@Column(name = "temperature", nullable = false)
	private Double temperature;

	@Column(name = "status", length = 32, nullable = false)
	private String status = STATUS_PENDING;

	@Column(name = "generation_job_id")
	private Long generationJobId;

	@Column(name = "run_id", length = 64)
	private String runId;

	@Column(name = "total_records")
	private Integer totalRecords;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public CampaignBackbone getCampaignBackbone() {
		return campaignBackbone;
	}

	public void setCampaignBackbone(CampaignBackbone campaignBackbone) {
		this.campaignBackbone = campaignBackbone;
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getGenerationJobId() {
		return generationJobId;
	}

	public void setGenerationJobId(Long generationJobId) {
		this.generationJobId = generationJobId;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public Integer getTotalRecords() {
		return totalRecords;
	}

	public void setTotalRecords(Integer totalRecords) {
		this.totalRecords = totalRecords;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}
