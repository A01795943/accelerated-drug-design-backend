package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CreateDataGenerationCampaignRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignTemperatureJob;
import edu.itesm.accelerated_drug_design_backend.entity.DataGenerationCampaign;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.CampaignBackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CampaignTemperatureJobRepository;
import edu.itesm.accelerated_drug_design_backend.repository.DataGenerationCampaignRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataGenerationCampaignSchedulerIntegrationTest {

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

	@MockitoBean
	private GenerationJobService generationJobService;

	@Test
	void planCampaign_enqueuesThreeJobsForOneCompletedBackboneNotAllFifteen() {
		Project project = new Project();
		project.setName("scheduler-test");
		project = projectRepository.save(project);

		CreateDataGenerationCampaignRequest request = new CreateDataGenerationCampaignRequest();
		request.setNumSeqsPerTemperature(4);
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
		List<CampaignBackbone> backbones = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaignId);
		assertEquals(5, backbones.size());
		assertEquals(15, campaignTemperatureJobRepository.countByCampaignId(campaignId));

		CampaignBackbone completed = backbones.getFirst();
		completed.setStatus(CampaignBackbone.STATUS_COMPLETED);
		completed.setBackboneId(777L);
		completed.setRunId("bb-run-0");
		campaignBackboneRepository.save(completed);

		AtomicLong ids = new AtomicLong(9000);
		when(generationJobService.createJob(eq(project.getId()), any())).thenAnswer(invocation -> {
			GenerationJob queued = new GenerationJob();
			queued.setId(ids.getAndIncrement());
			queued.setRunId("mpnn-run-" + queued.getId());
			queued.setStatus(GenerationJob.STATUS_QUEUED);
			return queued;
		});

		campaignService.planCampaign(campaignId);

		ArgumentCaptor<CreateGenerationJobRequest> captor = ArgumentCaptor.forClass(CreateGenerationJobRequest.class);
		verify(generationJobService, times(3)).createJob(eq(project.getId()), captor.capture());
		for (CreateGenerationJobRequest req : captor.getAllValues()) {
			assertEquals(777L, req.getBackboneId());
		}

		CampaignBackbone reloaded = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaignId).getFirst();
		long runningOnCompleted = reloaded.getTemperatureJobs().stream()
				.filter(tj -> CampaignTemperatureJob.STATUS_RUNNING.equals(tj.getStatus()))
				.count();
		assertEquals(3, runningOnCompleted);

		long stillPending = campaignTemperatureJobRepository.countByCampaignIdAndStatus(
				campaignId, CampaignTemperatureJob.STATUS_PENDING);
		assertEquals(12, stillPending);
		assertEquals(DataGenerationCampaign.STATUS_RUNNING,
				campaignRepository.findById(campaignId).orElseThrow().getStatus());
	}
}
