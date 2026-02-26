package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST to rf_diffusion core: /run/rfdiffusion
 */
public class RfdiffusionRunRequest {

	@JsonProperty("run_id")
	private String runId;

	@JsonProperty("run_name")
	private String runName;

	@JsonProperty("pdb_content")
	private String pdbContent;

	@JsonProperty("contigs")
	private String contigs;

	@JsonProperty("iterations")
	private Integer iterations;

	@JsonProperty("num_designs")
	private Integer numDesigns;

	@JsonProperty("hotspot")
	private String hotspot;

	@JsonProperty("chain_to_remove")
	private String chainToRemove;

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public String getRunName() {
		return runName;
	}

	public void setRunName(String runName) {
		this.runName = runName;
	}

	public String getPdbContent() {
		return pdbContent;
	}

	public void setPdbContent(String pdbContent) {
		this.pdbContent = pdbContent;
	}

	public String getContigs() {
		return contigs;
	}

	public void setContigs(String contigs) {
		this.contigs = contigs;
	}

	public Integer getIterations() {
		return iterations;
	}

	public void setIterations(Integer iterations) {
		this.iterations = iterations;
	}

	public Integer getNumDesigns() {
		return numDesigns;
	}

	public void setNumDesigns(Integer numDesigns) {
		this.numDesigns = numDesigns;
	}

	public String getHotspot() {
		return hotspot;
	}

	public void setHotspot(String hotspot) {
		this.hotspot = hotspot;
	}

	public String getChainToRemove() {
		return chainToRemove;
	}

	public void setChainToRemove(String chainToRemove) {
		this.chainToRemove = chainToRemove;
	}
}
