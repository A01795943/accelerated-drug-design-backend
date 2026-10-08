package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreInstanceHealthServiceIntegrationTest {

	@Autowired
	private CoreInstanceHealthService healthService;

	@Autowired
	private CoreDispatchService coreDispatchService;

	@Autowired
	private GenerationJobService generationJobService;

	@Autowired
	private CoreInstanceRepository coreInstanceRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private BackboneRepository backboneRepository;

	@Autowired
	private MinibatchDispatcher minibatchDispatcher;

	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;

	@Autowired
	private GenerationJobRepository generationJobRepository;

	@MockitoBean
	private CoreSystemInterface coreSystem;

	@BeforeEach
	void stubCore() {
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(null);
		when(coreSystem.getRfdiffusionStatus(any(), any())).thenReturn(null);
		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_DOWN);
		clearInvocations(coreSystem);
	}

	@Test
	void probeEnabledInstances_marksDownAndRecordsLastHealthAt() {
		CoreInstance seed = seedInstance();
		assertEquals(CoreInstance.HEALTH_UNKNOWN, seed.getHealthState());
		assertNull(seed.getLastHealthAt());

		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_DOWN);
		healthService.probeEnabledInstances();

		CoreInstance down = coreInstanceRepository.findById(seed.getId()).orElseThrow();
		assertEquals(CoreInstance.HEALTH_DOWN, down.getHealthState());
		assertNotNull(down.getLastHealthAt());
		verify(coreSystem, times(1)).healthCheck(argThat(i -> seed.getId().equals(i.getId())));
	}

	@Test
	void probeEnabledInstances_marksUpWhenHealthResponds() {
		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_UP);
		healthService.probeEnabledInstances();

		CoreInstance seed = seedInstance();
		assertEquals(CoreInstance.HEALTH_UP, seed.getHealthState());
		assertNotNull(seed.getLastHealthAt());
	}

	@Test
	void probeEnabledInstances_skipsDisabledInstances() {
		CoreInstance disabled = persistInstance("http://10.0.0.51:8000", false);
		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_UP);

		healthService.probeEnabledInstances();

		verify(coreSystem, never()).healthCheck(argThat(i -> disabled.getId().equals(i.getId())));
		assertEquals(CoreInstance.HEALTH_UNKNOWN,
				coreInstanceRepository.findById(disabled.getId()).orElseThrow().getHealthState());
		assertNull(coreInstanceRepository.findById(disabled.getId()).orElseThrow().getLastHealthAt());
	}

	@Test
	void downInstanceIsSkippedUntilItRecovers() {
		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_DOWN);
		healthService.probeEnabledInstances();
		assertEquals(CoreInstance.HEALTH_DOWN, seedInstance().getHealthState());

		GenerationJob job = createQueuedJob();
		minibatchDispatcher.dispatchPending();

		GenerationJobMinibatch stillPending = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_PENDING, stillPending.getStatus());
		assertNull(stillPending.getCoreInstanceId());
		verify(coreSystem, never()).triggerMpnn(any(), any());

		when(coreSystem.healthCheck(any())).thenReturn(CoreInstance.HEALTH_UP);
		healthService.probeEnabledInstances();
		assertEquals(CoreInstance.HEALTH_UP, seedInstance().getHealthState());

		minibatchDispatcher.dispatchPending();

		GenerationJobMinibatch running = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, running.getStatus());
		assertEquals(seedInstance().getId(), running.getCoreInstanceId());
		verify(coreSystem, times(1)).triggerMpnn(any(), any());
	}

	private CoreInstance seedInstance() {
		return coreInstanceRepository.findTopByOrderByIdAsc().orElseThrow();
	}

	private CoreInstance persistInstance(String baseUrl, boolean enabled) {
		CoreInstance instance = new CoreInstance();
		instance.setName("health-" + UUID.randomUUID());
		instance.setBaseUrl(baseUrl);
		instance.setEnabled(enabled);
		instance.setRestrictSchedule(false);
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		return coreInstanceRepository.save(instance);
	}

	private GenerationJob createQueuedJob() {
		Project project = new Project();
		project.setName("health-dispatch-" + UUID.randomUUID());
		project.setTarget("ATOM 1");
		project = projectRepository.save(project);

		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM      1  N   ALA A   1      11.000  12.000  13.000  1.00  0.00           N");
		backbone.setContigs("A1-10");
		backbone = backboneRepository.save(backbone);

		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(backbone.getId());
		request.setTemperature(0.1);
		request.setNumSeqs(4);
		return generationJobService.createJob(project.getId(), request);
	}
}
