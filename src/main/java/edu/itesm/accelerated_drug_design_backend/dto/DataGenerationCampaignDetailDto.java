package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

public class DataGenerationCampaignDetailDto {

	private Long id;
	private String status;
	private Integer numSeqsPerTemperature;
	private List<Double> temperatures;
	private List<CampaignBackboneConfigProgressDto> backboneConfigs;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getNumSeqsPerTemperature() {
		return numSeqsPerTemperature;
	}

	public void setNumSeqsPerTemperature(Integer numSeqsPerTemperature) {
		this.numSeqsPerTemperature = numSeqsPerTemperature;
	}

	public List<Double> getTemperatures() {
		return temperatures;
	}

	public void setTemperatures(List<Double> temperatures) {
		this.temperatures = temperatures;
	}

	public List<CampaignBackboneConfigProgressDto> getBackboneConfigs() {
		return backboneConfigs;
	}

	public void setBackboneConfigs(List<CampaignBackboneConfigProgressDto> backboneConfigs) {
		this.backboneConfigs = backboneConfigs;
	}
}
