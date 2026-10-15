package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record DataGenerationCampaignSummaryDto(
		Long id,
		String name,
		String status,
		Instant createdAt,
		Instant updatedAt,
		int totalBackbones,
		int completedBackbones,
		int totalTemperatureJobs,
		int completedTemperatureJobs,
		int totalSteps,
		int completedSteps,
		double progressPercent
) {
}
