package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.config.CoreInstanceProperties;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures at least the configured default core exists, then inserts any
 * {@code app.core.instances[]} entries whose base URL is not already registered.
 * V4 already seeds {@code core-principal} from {@code app.core.base-url}, so the
 * empty-table branch runs only if that row was removed.
 */
@Component
@EnableConfigurationProperties(CoreInstanceProperties.class)
public class CoreInstanceSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CoreInstanceSeeder.class);

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreInstanceProperties properties;
	private final CoreBaseUrlValidator baseUrlValidator;

	public CoreInstanceSeeder(
			CoreInstanceRepository coreInstanceRepository,
			CoreInstanceProperties properties,
			CoreBaseUrlValidator baseUrlValidator) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.properties = properties;
		this.baseUrlValidator = baseUrlValidator;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (coreInstanceRepository.count() == 0) {
			String baseUrl = properties.getBaseUrl();
			insert("default", baseUrl, 1);
			log.info("Seeded default core instance at {}", baseUrl);
		}
		if (properties.getInstances() == null) {
			return;
		}
		for (CoreInstanceProperties.DeclaredInstance declared : properties.getInstances()) {
			if (declared.getBaseUrl() == null || declared.getBaseUrl().isBlank()) {
				continue;
			}
			String baseUrl = baseUrlValidator.validateAndNormalize(declared.getBaseUrl());
			if (coreInstanceRepository.findByBaseUrl(baseUrl).isPresent()) {
				continue;
			}
			String name = declared.getName() == null || declared.getName().isBlank()
					? "core-" + baseUrl
					: declared.getName().trim();
			int maxRuns = declared.getMaxConcurrentRuns() == null || declared.getMaxConcurrentRuns() < 1
					? 1
					: declared.getMaxConcurrentRuns();
			insert(name, baseUrl, maxRuns);
			log.info("Registered core instance {} at {}", name, baseUrl);
		}
	}

	private void insert(String name, String baseUrl, int maxConcurrentRuns) {
		String normalized = baseUrlValidator.validateAndNormalize(baseUrl);
		CoreInstance instance = new CoreInstance();
		instance.setName(name);
		instance.setBaseUrl(normalized);
		instance.setEnabled(true);
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		instance.setStatus(CoreInstance.STATUS_AVAILABLE);
		instance.setMaxConcurrentRuns(maxConcurrentRuns);
		coreInstanceRepository.save(instance);
	}
}
