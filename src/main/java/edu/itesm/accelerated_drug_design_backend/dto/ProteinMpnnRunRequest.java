package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST to core: run_proteinmpnn_alphafold (use_alphafold=true).
 */
public class ProteinMpnnRunRequest {

	@JsonProperty("run_id")
	private String runId;

	@JsonProperty("run_name")
	private String runName;

	@JsonProperty("pdb_content")
	private String pdbContent;

	@JsonProperty("contigs")
	private String contigs;

	@JsonProperty("num_seqs")
	private Integer numSeqs;

	@JsonProperty("mpnn_sampling_temp")
	private Double mpnnSamplingTemp;

	@JsonProperty("use_alphafold")
	private Boolean useAlphafold = true;

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

	public Integer getNumSeqs() {
		return numSeqs;
	}

	public void setNumSeqs(Integer numSeqs) {
		this.numSeqs = numSeqs;
	}

	public Double getMpnnSamplingTemp() {
		return mpnnSamplingTemp;
	}

	public void setMpnnSamplingTemp(Double mpnnSamplingTemp) {
		this.mpnnSamplingTemp = mpnnSamplingTemp;
	}

	public Boolean getUseAlphafold() {
		return useAlphafold;
	}

	public void setUseAlphafold(Boolean useAlphafold) {
		this.useAlphafold = useAlphafold;
	}
}
