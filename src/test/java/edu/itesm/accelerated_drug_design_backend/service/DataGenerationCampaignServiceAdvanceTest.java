package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CreateBackbonesRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.*;
import edu.itesm.accelerated_drug_design_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataGenerationCampaignServiceAdvanceTest {

	@Mock
	private DataGenerationCampaignRepository campaignRepository;
	@Mock
	private CampaignBackboneRepository campaignBackboneRepository;
	@Mock
	private CampaignTemperatureJobRepository campaignTemperatureJobRepository;
	@Mock
	private ProjectService projectService;
	@Mock
	private BackboneService backboneService;
	@Mock
	private GenerationJobService generationJobService;
	@Mock
	private BackboneRepository backboneRepository;

	@InjectMocks
	private DataGenerationCampaignService service;

	private DataGenerationCampaign campaign;
	private CampaignBackbone completedBackbone;
	private CampaignTemperatureJob firstPendingJob;
	private CampaignTemperatureJob secondPendingJob;
	private CampaignTemperatureJob thirdPendingJob;

	@BeforeEach
	void setUp() {
		Project project = new Project();
		project.setId(10L);

		campaign = new DataGenerationCampaign();
		campaign.setId(1L);
		campaign.setStatus(DataGenerationCampaign.STATUS_RUNNING);
		campaign.setProject(project);
		campaign.setNumSeqsPerTemperature(4);

		completedBackbone = new CampaignBackbone();
		completedBackbone.setId(100L);
		completedBackbone.setCampaign(campaign);
		completedBackbone.setSortOrder(0);
		completedBackbone.setStatus(CampaignBackbone.STATUS_COMPLETED);
		completedBackbone.setBackboneId(500L);
		completedBackbone.setTemperatureJobs(new ArrayList<>());

		firstPendingJob = pendingJob(1000L, 0.1);
		secondPendingJob = pendingJob(1001L, 0.2);
		thirdPendingJob = pendingJob(1002L, 0.3);
	}

	@Test
	void planCampaign_enqueuesAllPendingJobsOnCompletedBackbone() {
		completedBackbone.getTemperatureJobs().addAll(List.of(firstPendingJob, secondPendingJob, thirdPendingJob));
		when(campaignRepository.findWithProjectById(1L)).thenReturn(Optional.of(campaign));
		when(campaignTemperatureJobRepository.findAllPendingForCompletedBackbones(1L))
				.thenReturn(List.of(firstPendingJob, secondPendingJob, thirdPendingJob));
		when(campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(1L))
				.thenReturn(List.of(completedBackbone));
		stubCreateJob();

		assertDoesNotThrow(() -> service.planCampaign(1L));

		ArgumentCaptor<CreateGenerationJobRequest> reqCaptor = ArgumentCaptor.forClass(CreateGenerationJobRequest.class);
		verify(generationJobService, times(3)).createJob(eq(10L), reqCaptor.capture());
		assertEquals(List.of(0.1, 0.2, 0.3), reqCaptor.getAllValues().stream()
				.map(CreateGenerationJobRequest::getTemperature)
				.toList());
		assertEquals(DataGenerationCampaign.STATUS_RUNNING, campaign.getStatus());
	}

	@Test
	void planCampaign_enqueuesPendingJobsEvenWhenOthersAreRunning() {
		CampaignTemperatureJob runningJob = new CampaignTemperatureJob();
		runningJob.setId(2000L);
		runningJob.setCampaignBackbone(completedBackbone);
		runningJob.setRunId("run-active");
		runningJob.setStatus(CampaignTemperatureJob.STATUS_RUNNING);
		completedBackbone.getTemperatureJobs().addAll(List.of(runningJob, firstPendingJob));

		GenerationJob polled = new GenerationJob();
		polled.setStatus("RUNNING");

		when(campaignRepository.findWithProjectById(1L)).thenReturn(Optional.of(campaign));
		when(campaignTemperatureJobRepository.findAllRunningForCompletedBackbones(1L))
				.thenReturn(List.of(runningJob));
		when(campaignTemperatureJobRepository.findAllPendingForCompletedBackbones(1L))
				.thenReturn(List.of(firstPendingJob));
		when(generationJobService.checkRunStatus(10L, "run-active")).thenReturn(polled);
		when(campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(1L))
				.thenReturn(List.of(completedBackbone));
		stubCreateJob();

		service.planCampaign(1L);

		verify(generationJobService).checkRunStatus(10L, "run-active");
		verify(generationJobService).createJob(eq(10L), any());
	}

	@Test
	void planCampaign_doesNotEnqueueJobsUntilBackboneCompleted() {
		CampaignBackbone pendingBackbone = new CampaignBackbone();
		pendingBackbone.setId(101L);
		pendingBackbone.setCampaign(campaign);
		pendingBackbone.setSortOrder(1);
		pendingBackbone.setStatus(CampaignBackbone.STATUS_PENDING);
		pendingBackbone.setContigs("cfg-b");
		pendingBackbone.setIterations(30);
		pendingBackbone.setTemperatureJobs(new ArrayList<>());

		completedBackbone.getTemperatureJobs().add(firstPendingJob);

		when(campaignRepository.findWithProjectById(1L)).thenReturn(Optional.of(campaign));
		when(campaignTemperatureJobRepository.findAllPendingForCompletedBackbones(1L))
				.thenReturn(List.of(firstPendingJob));
		when(campaignBackboneRepository.findByCampaign_IdAndStatusOrderBySortOrderAsc(
				1L, CampaignBackbone.STATUS_RUNNING))
				.thenReturn(List.of());
		when(campaignBackboneRepository.findByCampaign_IdAndStatusOrderBySortOrderAsc(
				1L, CampaignBackbone.STATUS_PENDING))
				.thenReturn(List.of(pendingBackbone));
		when(campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(1L))
				.thenReturn(List.of(completedBackbone, pendingBackbone));
		stubCreateJob();

		Backbone queued = new Backbone();
		queued.setId(800L);
		queued.setRunID("bb-run");
		queued.setStatus(Backbone.STATUS_QUEUED);
		when(backboneService.createBackbones(eq(10L), any(CreateBackbonesRequest.class)))
				.thenReturn(List.of(queued));

		service.planCampaign(1L);

		verify(generationJobService, times(1)).createJob(eq(10L), any());
		verify(backboneService).createBackbones(eq(10L), any());
		assertEquals(CampaignBackbone.STATUS_RUNNING, pendingBackbone.getStatus());
	}

	@Test
	void planCampaign_marksCampaignCompletedWhenAllUnitsAreTerminal() {
		firstPendingJob.setStatus(CampaignTemperatureJob.STATUS_COMPLETED);
		completedBackbone.getTemperatureJobs().add(firstPendingJob);
		when(campaignRepository.findWithProjectById(1L)).thenReturn(Optional.of(campaign));
		when(campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(1L))
				.thenReturn(List.of(completedBackbone));

		service.planCampaign(1L);

		verify(generationJobService, never()).createJob(anyLong(), any());
		assertEquals(DataGenerationCampaign.STATUS_COMPLETED, campaign.getStatus());
	}

	@Test
	void planCampaign_marksCampaignPartialWhenAUnitFailed() {
		firstPendingJob.setStatus(CampaignTemperatureJob.STATUS_ERROR);
		completedBackbone.getTemperatureJobs().add(firstPendingJob);
		when(campaignRepository.findWithProjectById(1L)).thenReturn(Optional.of(campaign));
		when(campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(1L))
				.thenReturn(List.of(completedBackbone));

		service.planCampaign(1L);

		assertEquals(DataGenerationCampaign.STATUS_PARTIAL, campaign.getStatus());
	}

	private CampaignTemperatureJob pendingJob(long id, double temperature) {
		CampaignTemperatureJob job = new CampaignTemperatureJob();
		job.setId(id);
		job.setCampaignBackbone(completedBackbone);
		job.setTemperature(temperature);
		job.setStatus(CampaignTemperatureJob.STATUS_PENDING);
		return job;
	}

	private void stubCreateJob() {
		AtomicLong ids = new AtomicLong(9000);
		when(generationJobService.createJob(eq(10L), any())).thenAnswer(invocation -> {
			GenerationJob created = new GenerationJob();
			created.setId(ids.getAndIncrement());
			created.setRunId("run-" + created.getId());
			created.setStatus(GenerationJob.STATUS_QUEUED);
			return created;
		});
	}
}
