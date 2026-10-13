package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.SettingView;
import edu.itesm.accelerated_drug_design_backend.entity.AppSetting;
import edu.itesm.accelerated_drug_design_backend.repository.AppSettingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code app_settings} with a short cache. Dispatch calls
 * {@link #maxAttemptFactor()} on every minibatch, so a change shows up
 * within about 30 seconds.
 */
@Service
public class SettingsService {

	private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

	public static final String MAX_ATTEMPT_FACTOR = "generation.max_attempt_factor";
	public static final String BACKFILL_DONE = "generation.binder_keys_backfill_done";
	static final long CACHE_TTL_MS = 30_000;
	static final int DEFAULT_FACTOR = 20;

	private final AppSettingRepository repository;
	private volatile Map<String, AppSetting> cache = Map.of();
	private volatile long loadedAt;
	private volatile String lastInvalidFactor;

	public SettingsService(AppSettingRepository repository) {
		this.repository = repository;
	}

	public int maxAttemptFactor() {
		AppSettingCatalog.SettingDefinition def = AppSettingCatalog.MAX_ATTEMPT_FACTOR;
		String raw = get(def.key());
		Integer parsed = parseInteger(raw, def);
		if (parsed != null) {
			return parsed;
		}
		if (raw != null && !raw.isBlank() && !raw.equals(lastInvalidFactor)) {
			lastInvalidFactor = raw;
			log.warn("Setting {} has invalid value '{}'; using default {}", def.key(), raw, def.defaultValue());
		}
		return DEFAULT_FACTOR;
	}

	@Transactional(readOnly = true)
	public List<SettingView> listDefined() {
		ensureFresh();
		List<SettingView> views = new ArrayList<>();
		for (AppSettingCatalog.SettingDefinition def : AppSettingCatalog.all()) {
			views.add(toView(def, cache.get(def.key())));
		}
		return views;
	}

	@Transactional
	public SettingView updateDefined(String key, String rawValue, String updatedBy) {
		AppSettingCatalog.SettingDefinition def = AppSettingCatalog.find(key)
				.orElseThrow(() -> new EntityNotFoundException("No existe el ajuste: " + key));
		String value = rawValue == null ? "" : rawValue.trim();
		if (parseInteger(value, def) == null) {
			throw new IllegalArgumentException(
					"El valor de " + def.key() + " debe ser un entero entre " + def.min() + " y " + def.max() + ".");
		}
		AppSetting row = repository.findById(def.key()).orElseGet(AppSetting::new);
		row.setSettingKey(def.key());
		row.setSettingValue(value);
		row.setDescription(def.description());
		row.setUpdatedAt(Instant.now());
		row.setUpdatedBy(updatedBy == null || updatedBy.isBlank() ? "unknown" : updatedBy);
		repository.save(row);
		invalidate();
		return toView(def, row);
	}

	public String get(String key) {
		ensureFresh();
		AppSetting row = cache.get(key);
		return row == null ? null : row.getSettingValue();
	}

	private void ensureFresh() {
		if (System.currentTimeMillis() - loadedAt > CACHE_TTL_MS) {
			reload();
		}
	}

	public synchronized void reload() {
		if (System.currentTimeMillis() - loadedAt <= CACHE_TTL_MS) {
			return;
		}
		Map<String, AppSetting> fresh = new HashMap<>();
		for (AppSetting row : repository.findAll()) {
			fresh.put(row.getSettingKey(), row);
		}
		cache = fresh;
		loadedAt = System.currentTimeMillis();
	}

	public void invalidate() {
		loadedAt = 0;
	}

	@Transactional
	public void put(String key, String value, String description) {
		AppSetting row = repository.findById(key).orElseGet(AppSetting::new);
		row.setSettingKey(key);
		row.setSettingValue(value);
		row.setDescription(description);
		row.setUpdatedAt(Instant.now());
		row.setUpdatedBy("system");
		repository.save(row);
		invalidate();
	}

	private static SettingView toView(AppSettingCatalog.SettingDefinition def, AppSetting row) {
		boolean stored = row != null && row.getSettingValue() != null && !row.getSettingValue().isBlank();
		return new SettingView(
				def.key(),
				stored ? row.getSettingValue() : def.defaultValue(),
				def.defaultValue(),
				def.type(),
				def.min(),
				def.max(),
				def.description(),
				def.group(),
				row == null ? null : row.getUpdatedAt(),
				row == null ? null : row.getUpdatedBy());
	}

	private static Integer parseInteger(String raw, AppSettingCatalog.SettingDefinition def) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			if (def.min() != null && value < def.min()) {
				return null;
			}
			if (def.max() != null && value > def.max()) {
				return null;
			}
			return value;
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
