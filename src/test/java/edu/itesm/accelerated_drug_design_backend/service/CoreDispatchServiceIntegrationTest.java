package edu.itesm.accelerated_drug_design_backend.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateBackbonesRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoreDispatchServiceIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CoreDispatchService coreDispatchService;

	@Autowired
	private BackboneService backboneService;

	@Autowired
	private GenerationJobService generationJobService;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private BackboneRepository backboneRepository;

	@Autowired
	private MinibatchDispatcher minibatchDispatcher;

	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;

	@Autowired
	private CoreInstanceRepository coreInstanceRepository;

	@MockitoBean
	private CoreSystemInterface coreSystem;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@BeforeEach
	void stubCoreAsIdle() {
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(null);
		when(coreSystem.getRfdiffusionStatus(any(), any())).thenReturn(null);
		clearInvocations(coreSystem);
	}

	@Test
	void createJob_splitsMinibatchesAndDoesNotCallCore() {
		GenerationJob job = createQueuedJob();

		assertEquals(GenerationJob.STATUS_RUNNING, job.getStatus());
		assertEquals(1, job.getTotalMinibatches());
		assertEquals(0, job.getCompletedMinibatches());
		assertNull(job.getCoreInstanceId());
		List<GenerationJobMinibatch> minibatches = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		assertEquals(1, minibatches.size());
		assertEquals(job.getRunId() + "_m0", minibatches.get(0).getRunId());
		assertEquals(GenerationJobMinibatch.STATUS_PENDING, minibatches.get(0).getStatus());
		verify(coreSystem, never()).triggerMpnn(any(), any());
		verify(coreSystem, never()).triggerRfdiffusion(any(), any());
	}

	@Test
	void statusEndpoint_returnsAggregateWithoutPollingCore() throws Exception {
		GenerationJob job = createQueuedJob();

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs/status/{runId}",
						job.getProject().getId(), job.getRunId())
						.header("Authorization", "Bearer " + userToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(GenerationJob.STATUS_RUNNING))
				.andExpect(jsonPath("$.totalMinibatches").value(1))
				.andExpect(jsonPath("$.completedMinibatches").value(0));

		verify(coreSystem, never()).getMpnnStatus(any(), any());
	}

	@Test
	void dispatchQueued_doesNotPostMinibatchJobs() {
		createQueuedJob();

		coreDispatchService.dispatchQueued();

		verify(coreSystem, never()).triggerMpnn(any(), any());
	}

	@Test
	void minibatchDispatch_assignsFreeInstanceAndPostsChunk() {
		GenerationJob job = createQueuedJob();

		minibatchDispatcher.dispatchPending();

		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, minibatch.getStatus());
		assertNotNull(minibatch.getCoreInstanceId());
		assertEquals(1, minibatch.getAttempts());

		ArgumentCaptor<ProteinMpnnRunRequest> req = ArgumentCaptor.forClass(ProteinMpnnRunRequest.class);
		verify(coreSystem, times(1)).triggerMpnn(any(), req.capture());
		assertEquals(job.getRunId() + "_m0", req.getValue().getRunId());
		assertEquals(4, req.getValue().getNumSeqs());
		assertNotNull(req.getValue().getSeed());
	}

	@Test
	void minibatchDispatch_twoFreeInstancesRunTwoJobsInParallel() {
		GenerationJob job1 = createQueuedJob();
		GenerationJob job2 = createQueuedJobOnSameBackbone(job1);
		persistFreeInstance("http://10.0.0.31:8000");

		minibatchDispatcher.dispatchPending();

		GenerationJobMinibatch m1 = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job1.getId()).get(0);
		GenerationJobMinibatch m2 = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job2.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, m1.getStatus());
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, m2.getStatus());
		assertTrue(!m1.getCoreInstanceId().equals(m2.getCoreInstanceId()));
		verify(coreSystem, times(2)).triggerMpnn(any(), any());
	}

	@Test
	void minibatchDispatch_oneInstanceRespectsMaxConcurrentRuns() {
		GenerationJob job1 = createQueuedJob();
		GenerationJob job2 = createQueuedJobOnSameBackbone(job1);

		minibatchDispatcher.dispatchPending();

		List<GenerationJobMinibatch> minibatches = List.of(
				minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job1.getId()).get(0),
				minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job2.getId()).get(0));
		long running = minibatches.stream().filter(m -> GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus())).count();
		long pending = minibatches.stream().filter(m -> GenerationJobMinibatch.STATUS_PENDING.equals(m.getStatus())).count();
		assertEquals(1, running);
		assertEquals(1, pending);
		verify(coreSystem, times(1)).triggerMpnn(any(), any());
	}

	@Test
	void minibatchDispatch_postFailureDoesNotConsumeAttempt() {
		GenerationJob job = createQueuedJob();
		doThrow(new RuntimeException("core refused")).when(coreSystem).triggerMpnn(any(), any());

		minibatchDispatcher.dispatchPending();

		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_PENDING, minibatch.getStatus());
		assertNull(minibatch.getCoreInstanceId());
		assertEquals(0, minibatch.getAttempts());
		assertTrue(coreInstanceRepository.findAll().stream().anyMatch(i -> i.getConsecutiveFailures() >= 1));
	}

	@Test
	void createBackbones_staysQueuedAndDoesNotCallCore() {
		List<Backbone> created = createQueuedBackbones(2);

		assertEquals(2, created.size());
		assertTrue(created.stream().allMatch(bb -> Backbone.STATUS_QUEUED.equals(bb.getStatus())));
		assertTrue(created.stream().allMatch(bb -> bb.getCoreInstanceId() == null));
		assertEquals(created.get(0).getRunID(), created.get(1).getRunID());
		verify(coreSystem, never()).triggerRfdiffusion(any(), any());
	}

	@Test
	void dispatchQueued_backboneRunPostsOnceForSharedRunId() {
		List<Backbone> created = createQueuedBackbones(2);
		String runId = created.get(0).getRunID();

		coreDispatchService.dispatchQueued();

		List<Backbone> running = backboneRepository.findByRunIDOrderByIdAsc(runId);
		assertTrue(running.stream().allMatch(bb -> Backbone.STATUS_RUNNING.equals(bb.getStatus())));
		assertTrue(running.stream().allMatch(bb -> bb.getCoreInstanceId() != null));

		ArgumentCaptor<RfdiffusionRunRequest> req = ArgumentCaptor.forClass(RfdiffusionRunRequest.class);
		verify(coreSystem, times(1)).triggerRfdiffusion(any(), req.capture());
		assertEquals(runId, req.getValue().getRunId());
		assertEquals(2, req.getValue().getNumDesigns());
	}

	private GenerationJob createQueuedJob() {
		Project project = projectRepository.save(newProject());
		Backbone backbone = persistCompletedBackbone(project);
		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(backbone.getId());
		request.setTemperature(0.1);
		request.setNumSeqs(4);
		return generationJobService.createJob(project.getId(), request);
	}

	private GenerationJob createQueuedJobOnSameBackbone(GenerationJob existing) {
		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(existing.getBackboneId());
		request.setTemperature(0.2);
		request.setNumSeqs(4);
		return generationJobService.createJob(existing.getProject().getId(), request);
	}

	private List<Backbone> createQueuedBackbones(int count) {
		Project project = projectRepository.save(newProject());
		project.setTarget("ATOM 1");
		project = projectRepository.save(project);
		CreateBackbonesRequest request = new CreateBackbonesRequest();
		request.setCount(count);
		request.setContigs("A1-10");
		request.setIterations(10);
		return backboneService.createBackbones(project.getId(), request);
	}

	private Backbone persistCompletedBackbone(Project project) {
		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM      1  N   ALA A   1      11.000  12.000  13.000  1.00  0.00           N");
		backbone.setContigs("A1-10");
		return backboneRepository.save(backbone);
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

	private static Project newProject() {
		Project project = new Project();
		project.setName("dispatch-test-" + UUID.randomUUID());
		project.setTarget("ATOM 1");
		return project;
	}

	private String userToken() {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		Algorithm algorithm = Algorithm.HMAC512(secretBytes);
		return JWT.create()
				.withSubject("dispatch-test")
				.withClaim("roles", "ROLE_USER")
				.sign(algorithm);
	}
}
