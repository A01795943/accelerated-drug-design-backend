package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateDataGenerationCampaignRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone;
import edu.itesm.accelerated_drug_design_backend.entity.CampaignTemperatureJob;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.DataGenerationCampaign;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CampaignBackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.DataGenerationCampaignRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampaignPlannerDispatchIntegrationTest {

	@Autowired
	private DataGenerationCampaignService campaignService;

	@Autowired
	private CoreDispatchService coreDispatchService;

	@Autowired
	private MinibatchDispatcher minibatchDispatcher;

	@Autowired
	private MinibatchCollector minibatchCollector;

	@Autowired
	private edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository minibatchRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private BackboneRepository backboneRepository;

	@Autowired
	private GenerationJobRepository generationJobRepository;

	@Autowired
	private CampaignBackboneRepository campaignBackboneRepository;

	@Autowired
	private DataGenerationCampaignRepository campaignRepository;

	@Autowired
	private CoreInstanceRepository coreInstanceRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private CoreSystemInterface coreSystem;

	@BeforeEach
	void stubCoreAsIdle() {
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(null);
		when(coreSystem.getRfdiffusionStatus(any(), any())).thenReturn(null);
		clearInvocations(coreSystem);
		// Status updates from the metrics poller commit outside the test transaction.
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			for (CoreInstance instance : coreInstanceRepository.findAll()) {
				instance.setStatus(CoreInstance.STATUS_AVAILABLE);
				instance.setStatusReason(null);
				instance.setConsecutiveFailures(0);
			}
			for (edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch minibatch : minibatchRepository.findAll()) {
				if (edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch.STATUS_PENDING.equals(minibatch.getStatus())
						|| edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
					minibatch.setStatus(edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch.STATUS_ERROR);
					minibatch.setCoreInstanceId(null);
				}
			}
		});
		entityManager.clear();
	}

	@Test
	void threeFreeInstances_runThreeTemperatureJobsInParallel() {
		PreparedCampaign prepared = prepareCampaignWithCompletedBackbone();
		campaignService.planCampaign(prepared.campaignId());

		List<GenerationJob> created = generationJobRepository.findByProject_IdOrderByIdDesc(prepared.projectId());
		assertEquals(3, created.size());
		assertTrue(created.stream().allMatch(j -> GenerationJob.STATUS_QUEUED.equals(j.getStatus())));
		assertTrue(created.stream().allMatch(j -> j.getTotalMinibatches() != null && j.getTotalMinibatches() >= 1));

		persistFreeInstance("http://10.0.0.41:8000");
		persistFreeInstance("http://10.0.0.42:8000");

		minibatchDispatcher.dispatchPending();

		verify(coreSystem, times(3)).triggerMpnn(any(), any());
		assertTrue(generationJobRepository.findByProject_IdOrderByIdDesc(prepared.projectId()).stream()
				.allMatch(j -> GenerationJob.STATUS_RUNNING.equals(j.getStatus())));

		assertEquals(DataGenerationCampaign.STATUS_RUNNING,
				campaignRepository.findById(prepared.campaignId()).orElseThrow().getStatus());

		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(Map.of(
				"status", "COMPLETED",
				"summary", Map.of("fasta_content", ">design:0 n:0|ptm:0.5|i_pae:1\nAC\n"),
				"pagination", Map.of("total_batches", 1, "total_records", 1)));
		when(coreSystem.getMpnnStatusDetail(any(), any(), anyInt())).thenReturn(Map.of(
				"detail", List.of(Map.of("n", 0, "seq", "AC", "ptm", 0.5, "pdb_content", "ATOM"))));
		minibatchCollector.collect();
		campaignService.planCampaign(prepared.campaignId());

		assertEquals(DataGenerationCampaign.STATUS_COMPLETED,
				campaignRepository.findById(prepared.campaignId()).orElseThrow().getStatus());
		List<CampaignTemperatureJob> jobs = campaignBackboneRepository
				.findByCampaign_IdOrderBySortOrderAsc(prepared.campaignId())
				.getFirst()
				.getTemperatureJobs();
		assertTrue(jobs.stream().allMatch(tj -> CampaignTemperatureJob.STATUS_COMPLETED.equals(tj.getStatus())));
	}

	@Test
	void oneInstance_serializesThreeTemperatureJobs() {
		PreparedCampaign prepared = prepareCampaignWithCompletedBackbone();
		campaignService.planCampaign(prepared.campaignId());
		minibatchDispatcher.dispatchPending();

		List<edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch> chunks = minibatchRepository.findAll();
		long running = chunks.stream().filter(m -> edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus())).count();
		long pending = chunks.stream().filter(m -> edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch.STATUS_PENDING.equals(m.getStatus())).count();
		assertEquals(1, running);
		assertEquals(2, pending);
		verify(coreSystem, times(1)).triggerMpnn(any(), any());
		assertEquals(DataGenerationCampaign.STATUS_RUNNING,
				campaignRepository.findById(prepared.campaignId()).orElseThrow().getStatus());
	}
	private PreparedCampaign prepareCampaignWithCompletedBackbone() {
		Project project = new Project();
		project.setName("planner-dispatch-" + UUID.randomUUID());
		project.setTarget("ATOM 1");
		project = projectRepository.save(project);

		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM      1  N   ALA A   1      11.000  12.000  13.000  1.00  0.00           N");
		backbone.setContigs("A1-10");
		backbone = backboneRepository.save(backbone);

		CreateDataGenerationCampaignRequest request = new CreateDataGenerationCampaignRequest();
		request.setNumSeqsPerTemperature(4);
		request.setTemperatures(List.of(0.1, 0.2, 0.3));
		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg =
				new CreateDataGenerationCampaignRequest.BackboneConfigDto();
		cfg.setContigs("A1-10");
		cfg.setNumBackbones(1);
		request.setBackboneConfigs(List.of(cfg));

		Long campaignId = campaignService.createCampaign(project.getId(), request).getCampaignId();
		CampaignBackbone campaignBackbone = campaignBackboneRepository
				.findByCampaign_IdOrderBySortOrderAsc(campaignId)
				.getFirst();
		campaignBackbone.setStatus(CampaignBackbone.STATUS_COMPLETED);
		campaignBackbone.setBackboneId(backbone.getId());
		campaignBackbone.setRunId("bb-done");
		campaignBackboneRepository.save(campaignBackbone);
		return new PreparedCampaign(project.getId(), campaignId);
	}

	private CoreInstance persistFreeInstance(String baseUrl) {
		CoreInstance instance = new CoreInstance();
		instance.setName("extra-" + UUID.randomUUID());
		instance.setBaseUrl(baseUrl);
		instance.setEnabled(true);
		instance.setRestrictSchedule(false);
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		return coreInstanceRepository.save(instance);
	}

	private record PreparedCampaign(Long projectId, Long campaignId) {
	}
}
