package edu.itesm.accelerated_drug_design_backend.config;

import edu.itesm.accelerated_drug_design_backend.service.CoreInstanceHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthScheduler {

	private static final Logger log = LoggerFactory.getLogger(HealthScheduler.class);

	private final CoreInstanceHealthService healthService;

	public HealthScheduler(CoreInstanceHealthService healthService) {
		this.healthService = healthService;
	}

	@Scheduled(
			fixedDelayString = "${app.core.health-interval-ms:30000}",
			initialDelayString = "${app.core.health-interval-ms:30000}")
	public void tick() {
		try {
			healthService.probeEnabledInstances();
		} catch (Exception e) {
			log.warn("Health scheduler tick failed: {}", e.getMessage(), e);
		}
	}
}
