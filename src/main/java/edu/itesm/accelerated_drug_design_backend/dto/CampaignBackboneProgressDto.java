package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

public class CampaignBackboneProgressDto {

	private Long id;
	private String contigs;
	private String hotspots;
	private String chainsToRemove;
	private String status;
	private Long backboneId;
	private String error;
	private List<CampaignTemperatureJobProgressDto> jobs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public List<CampaignTemperatureJobProgressDto> getJobs() {
		return jobs;
	}

	public void setJobs(List<CampaignTemperatureJobProgressDto> jobs) {
		this.jobs = jobs;
	}
}
