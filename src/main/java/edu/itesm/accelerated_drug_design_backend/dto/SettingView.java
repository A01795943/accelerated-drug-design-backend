package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record SettingView(
		String key,
		String value,
		String defaultValue,
		String type,
		Integer min,
		Integer max,
		String description,
		String group,
		Instant updatedAt,
		String updatedBy) {
}
