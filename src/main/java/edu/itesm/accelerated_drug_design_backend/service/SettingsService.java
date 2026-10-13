package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.AppSetting;
import edu.itesm.accelerated_drug_design_backend.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code app_settings} with a short cache. Dispatch calls
 * {@link #maxAttemptFactor()} on every minibatch, so a change shows up
 * within about 30 seconds.
 */
@Service
public class SettingsService {

	public static final String MAX_ATTEMPT_FACTOR = "generation.max_attempt_factor";
	public static final String BACKFILL_DONE = "generation.binder_keys_backfill_done";
	static final long CACHE_TTL_MS = 30_000;
	static final int DEFAULT_FACTOR = 20;

	private final AppSettingRepository repository;
	private volatile Map<String, String> cache = Map.of();
	private volatile long loadedAt;

	public SettingsService(AppSettingRepository repository) {
		this.repository = repository;
	}

	public int maxAttemptFactor() {
		String raw = get(MAX_ATTEMPT_FACTOR);
		if (raw == null || raw.isBlank()) {
			return DEFAULT_FACTOR;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			if (value < 1 || value > 1000) {
				return DEFAULT_FACTOR;
			}
			return value;
		} catch (NumberFormatException ex) {
			return DEFAULT_FACTOR;
		}
	}

	public String get(String key) {
		if (System.currentTimeMillis() - loadedAt > CACHE_TTL_MS) {
			reload();
		}
		return cache.get(key);
	}

	public synchronized void reload() {
		if (System.currentTimeMillis() - loadedAt <= CACHE_TTL_MS) {
			return;
		}
		Map<String, String> fresh = new HashMap<>();
		for (AppSetting row : repository.findAll()) {
			fresh.put(row.getSettingKey(), row.getSettingValue());
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
}
