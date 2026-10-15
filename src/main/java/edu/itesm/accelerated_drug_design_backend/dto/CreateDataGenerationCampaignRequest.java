package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreateDataGenerationCampaignRequest {

	@NotEmpty
	@Valid
	private List<BackboneConfigDto> backboneConfigs;

	@NotEmpty
	private List<Double> temperatures;

	@NotNull
	@Min(1)
	private Integer numSeqsPerTemperature = 16;

	public List<BackboneConfigDto> getBackboneConfigs() {
		return backboneConfigs;
	}

	public void setBackboneConfigs(List<BackboneConfigDto> backboneConfigs) {
		this.backboneConfigs = backboneConfigs;
	}

	public List<Double> getTemperatures() {
		return temperatures;
	}

	public void setTemperatures(List<Double> temperatures) {
		this.temperatures = temperatures;
	}

	public Integer getNumSeqsPerTemperature() {
		return numSeqsPerTemperature;
	}

	public void setNumSeqsPerTemperature(Integer numSeqsPerTemperature) {
		this.numSeqsPerTemperature = numSeqsPerTemperature;
	}

	public static class BackboneConfigDto {
		private String contigs;
		private String hotspots;
		private String chainsToRemove;

		@Min(1)
		private Integer iterations = 30;

		/**
		 * RFdiffusion runs for this config (same contig/hotspots/chains). When omitted,
		 * legacy clients default to 1 in {@link edu.itesm.accelerated_drug_design_backend.service.DataGenerationCampaignService}.
		 * Capped at 20 to bound campaign row count and scheduler work per config.
		 */
		@Min(1)
		@Max(20)
		private Integer numBackbones;

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

		public Integer getNumBackbones() {
			return numBackbones;
		}

		public void setNumBackbones(Integer numBackbones) {
			this.numBackbones = numBackbones;
		}
	}
}
