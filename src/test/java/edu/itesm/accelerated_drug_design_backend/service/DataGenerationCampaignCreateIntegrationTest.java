package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CreateDataGenerationCampaignRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignTemperatureJob;
import edu.itesm.accelerated_drug_design_backend.entity.DataGenerationCampaign;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.CampaignBackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CampaignTemperatureJobRepository;
import edu.itesm.accelerated_drug_design_backend.repository.DataGenerationCampaignRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataGenerationCampaignCreateIntegrationTest {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private DataGenerationCampaignService campaignService;

	@Autowired
	private DataGenerationCampaignRepository campaignRepository;

	@Autowired
	private CampaignBackboneRepository campaignBackboneRepository;

	@Autowired
	private CampaignTemperatureJobRepository campaignTemperatureJobRepository;

	@Test
	void createCampaign_withMultipleBackbonesPerConfig_persistsExpectedRows() {
		Project project = projectRepository.save(newProject());

		CreateDataGenerationCampaignRequest request = new CreateDataGenerationCampaignRequest();
		request.setNumSeqsPerTemperature(8);
		request.setTemperatures(List.of(0.1, 0.2, 0.3));

		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg1 =
				new CreateDataGenerationCampaignRequest.BackboneConfigDto();
		cfg1.setContigs("cfg-a");
		cfg1.setNumBackbones(3);

		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg2 =
				new CreateDataGenerationCampaignRequest.BackboneConfigDto();
		cfg2.setContigs("cfg-b");
		cfg2.setNumBackbones(2);

		request.setBackboneConfigs(List.of(cfg1, cfg2));

		Long campaignId = campaignService.createCampaign(project.getId(), request).getCampaignId();

		long backboneCount = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaignId).size();
		long tempJobCount = campaignTemperatureJobRepository.countByCampaignId(campaignId);

		assertEquals(5, backboneCount);
		assertEquals(15, tempJobCount);

		List<CampaignBackbone> backbones = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaignId);
		long config0 = backbones.stream().filter(bb -> bb.getConfigIndex() == 0).count();
		long config1 = backbones.stream().filter(bb -> bb.getConfigIndex() == 1).count();
		assertEquals(3, config0);
		assertEquals(2, config1);
	}

	private static Project newProject() {
		Project project = new Project();
		project.setName("multi-backbone-create-test");
		return project;
	}
}
