package edu.itesm.accelerated_drug_design_backend.config;

import edu.itesm.accelerated_drug_design_backend.service.CoreDispatchService;
import edu.itesm.accelerated_drug_design_backend.service.DataGenerationCampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class DispatcherScheduler {

	private static final Logger log = LoggerFactory.getLogger(DispatcherScheduler.class);

	private final CoreDispatchService coreDispatchService;
	private final DataGenerationCampaignService campaignService;

	public DispatcherScheduler(
			CoreDispatchService coreDispatchService,
			DataGenerationCampaignService campaignService) {
		this.coreDispatchService = coreDispatchService;
		this.campaignService = campaignService;
	}

	@Scheduled(
			fixedDelayString = "${app.campaign.poll-interval-ms:15000}",
			initialDelayString = "${app.campaign.poll-interval-ms:15000}")
	public void tick() {
		try {
			coreDispatchService.reconcileOccupied();
		} catch (Exception e) {
			log.warn("Dispatcher reconcile failed: {}", e.getMessage(), e);
		}
		try {
			coreDispatchService.reapStaleClaims();
		} catch (Exception e) {
			log.warn("Dispatcher reaper failed: {}", e.getMessage(), e);
		}
		try {
			campaignService.planRunningCampaigns();
		} catch (Exception e) {
			log.warn("Campaign planner failed: {}", e.getMessage(), e);
		}
		try {
			coreDispatchService.dispatchQueued();
		} catch (Exception e) {
			log.warn("Dispatcher dispatch failed: {}", e.getMessage(), e);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void reconcileOnStartup() {
		try {
			coreDispatchService.reconcileOccupied();
		} catch (Exception e) {
			log.warn("Startup reconcile failed: {}", e.getMessage(), e);
		}
	}
}
