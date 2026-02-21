package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create one or more backbones for a project.
 */
public class CreateBackbonesRequest {

	private String contigs;
	private String hotspots;
	private String chainsToRemove;

	@NotNull(message = "count is required")
	@Min(value = 1, message = "count must be at least 1")
	private Integer count = 1;

	@Min(value = 1, message = "iterations must be at least 1")
	private Integer iterations = 30;

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

	public Integer getCount() {
		return count;
	}

	public void setCount(Integer count) {
		this.count = count;
	}

	public Integer getIterations() {
		return iterations;
	}

	public void setIterations(Integer iterations) {
		this.iterations = iterations;
	}
}
