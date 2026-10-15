package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampaignTemperatureJobRepositoryTest {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private DataGenerationCampaignRepository campaignRepository;

	@Autowired
	private CampaignTemperatureJobRepository temperatureJobRepository;

	@Test
	void findFirstByCampaignIdAndStatus_returnsSingleDeterministicRowWithManyMatches() {
		DataGenerationCampaign campaign = saveCampaignWithTemperatureJobs(20, CampaignTemperatureJob.STATUS_PENDING);

		Optional<CampaignTemperatureJob> first = temperatureJobRepository.findFirstByCampaignIdAndStatus(
				campaign.getId(), CampaignTemperatureJob.STATUS_PENDING);

		assertTrue(first.isPresent());
		assertEquals(0.05, first.get().getTemperature(), 1e-9);
		assertEquals(CampaignTemperatureJob.STATUS_PENDING, first.get().getStatus());
	}

	@Test
	void findFirstByCampaignIdAndStatus_runningPicksLowestTemperatureAmongMany() {
		DataGenerationCampaign campaign = saveCampaignWithTemperatureJobs(20, CampaignTemperatureJob.STATUS_COMPLETED);
		List<CampaignTemperatureJob> all = temperatureJobRepository.findAll().stream()
				.filter(tj -> tj.getCampaignBackbone().getCampaign().getId().equals(campaign.getId()))
				.toList();
		for (int i = 0; i < all.size(); i++) {
			CampaignTemperatureJob tj = all.get(i);
			if (i < 3) {
				tj.setStatus(CampaignTemperatureJob.STATUS_RUNNING);
				temperatureJobRepository.save(tj);
			}
		}

		Optional<CampaignTemperatureJob> running = temperatureJobRepository.findFirstByCampaignIdAndStatus(
				campaign.getId(), CampaignTemperatureJob.STATUS_RUNNING);

		assertTrue(running.isPresent());
		assertEquals(0.05, running.get().getTemperature(), 1e-9);
	}

	private DataGenerationCampaign saveCampaignWithTemperatureJobs(int temperatureCount, String initialStatus) {
		Project project = new Project();
		project.setName("repo-test-project");
		project = projectRepository.save(project);

		DataGenerationCampaign campaign = new DataGenerationCampaign();
		campaign.setProject(project);
		campaign.setStatus(DataGenerationCampaign.STATUS_RUNNING);
		campaign.setCreatedAt(Instant.now());
		campaign.setNumSeqsPerTemperature(8);
		campaign.setTemperaturesJson("[0.05,0.1]");

		CampaignBackbone backbone = new CampaignBackbone();
		backbone.setCampaign(campaign);
		backbone.setSortOrder(0);
		backbone.setContigs("20-35/0 A1-100");
		backbone.setStatus(CampaignBackbone.STATUS_COMPLETED);
		campaign.getBackbones().add(backbone);

		List<CampaignTemperatureJob> jobs = new ArrayList<>();
		for (int i = 0; i < temperatureCount; i++) {
			CampaignTemperatureJob tj = new CampaignTemperatureJob();
			tj.setCampaignBackbone(backbone);
			tj.setTemperature(0.05 + (i * 0.05));
			tj.setStatus(initialStatus);
			jobs.add(tj);
		}
		backbone.setTemperatureJobs(jobs);

		return campaignRepository.save(campaign);
	}
}
