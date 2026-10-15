package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

public class CampaignBackboneConfigProgressDto {

	private String contigs;
	private String hotspots;
	private String chainsToRemove;
	private Integer numBackbones;
	private Integer completedBackbones;
	private List<CampaignPhysicalBackboneDto> backbones;

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

	public Integer getNumBackbones() {
		return numBackbones;
	}

	public void setNumBackbones(Integer numBackbones) {
		this.numBackbones = numBackbones;
	}

	public Integer getCompletedBackbones() {
		return completedBackbones;
	}

	public void setCompletedBackbones(Integer completedBackbones) {
		this.completedBackbones = completedBackbones;
	}

	public List<CampaignPhysicalBackboneDto> getBackbones() {
		return backbones;
	}

	public void setBackbones(List<CampaignPhysicalBackboneDto> backbones) {
		this.backbones = backbones;
	}
}
