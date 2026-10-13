package edu.itesm.accelerated_drug_design_backend.service;

import java.util.List;
import java.util.Optional;

/**
 * Settings the admin screen may read and write. Keys that exist only for
 * internal bookkeeping (for example the binder-key backfill flag) stay out
 * of this list.
 */
public final class AppSettingCatalog {

	public static final SettingDefinition MAX_ATTEMPT_FACTOR = new SettingDefinition(
			SettingsService.MAX_ATTEMPT_FACTOR,
			"integer",
			1,
			1000,
			"20",
			"Veces que num_seqs puede intentar ProteinMPNN antes de quedarse con las secuencias únicas que alcanzó.",
			"Generación");

	private static final List<SettingDefinition> ALL = List.of(MAX_ATTEMPT_FACTOR);

	private AppSettingCatalog() {
	}

	public static List<SettingDefinition> all() {
		return ALL;
	}

	public static Optional<SettingDefinition> find(String key) {
		if (key == null) {
			return Optional.empty();
		}
		return ALL.stream().filter(def -> def.key().equals(key)).findFirst();
	}

	public record SettingDefinition(
			String key,
			String type,
			Integer min,
			Integer max,
			String defaultValue,
			String description,
			String group) {
	}
}
