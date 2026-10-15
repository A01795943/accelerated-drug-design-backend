package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * JPQL projection for batched campaign progress counts (one row per campaign).
 */
public record CampaignProgressAggregate(
		Long campaignId,
		long totalBackbones,
		long completedBackbones,
		long totalTemperatureJobs,
		long completedTemperatureJobs
) {
}
