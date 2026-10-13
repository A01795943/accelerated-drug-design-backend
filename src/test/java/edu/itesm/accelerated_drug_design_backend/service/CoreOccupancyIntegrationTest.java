package edu.itesm.accelerated_drug_design_backend.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceMetricsHistoryRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoreOccupancyIntegrationTest {

	@Autowired
	private CoreOccupancyService occupancyService;
	@Autowired
	private CoreMetricsPoller metricsPoller;
	@Autowired
	private CoreInstanceRepository coreInstanceRepository;
	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;
	@Autowired
	private CoreInstanceMetricsHistoryRepository historyRepository;
	@Autowired
	private GenerationJobService generationJobService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private BackboneRepository backboneRepository;
	@Autowired
	private MockMvc mockMvc;
	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void occupancyCoversEmptyOneManyBackboneAndFull() {
		CoreInstance idle = instance("idle", 1);
		assertEquals(CoreOccupancyService.FREE, occupancyService.byInstance(java.util.List.of(idle)).get(idle.getId()).state());

		CoreInstance one = instance("one", 2);
		GenerationJob job = createJob(2, "binder-a");
		GenerationJobMinibatch first = running(job, one, 0);
		var single = occupancyService.byInstance(java.util.List.of(one)).get(one.getId());
		assertEquals(CoreOccupancyService.IN_USE, single.state());
		assertEquals(1, single.inFlightTotal());
		assertEquals(job.getId(), single.runningMinibatches().get(0).generationJobId());
		assertEquals("binder-a", single.runningMinibatches().get(0).backboneName());
		assertEquals(0.4, single.runningMinibatches().get(0).temperature());

		running(job, one, 1);
		assertEquals(2, occupancyService.byInstance(java.util.List.of(one)).get(one.getId()).inFlightTotal());

		CoreInstance backbone = instance("bb", 2);
		backbone.setCurrentRunId("rf-" + UUID.randomUUID());
		backbone.setCurrentRunKind("backbone");
		backbone = coreInstanceRepository.save(backbone);
		var claimed = occupancyService.byInstance(java.util.List.of(backbone)).get(backbone.getId());
		assertEquals(CoreOccupancyService.IN_USE, claimed.state());
		assertEquals("backbone", claimed.otherRun().kind());
		assertEquals(0, claimed.runningMinibatches().size());

		CoreInstance full = instance("full", 1);
		running(job, full, 2);
		assertEquals(CoreOccupancyService.FULL, occupancyService.byInstance(java.util.List.of(full)).get(full.getId()).state());
		assertEquals(first.getStatus(), GenerationJobMinibatch.STATUS_RUNNING);
	}

	@Test
	void coresEndpointMatchesTheDispatcherCountAndKeepsARunningMinibatch() throws Exception {
		CoreInstance instance = instance("match", 2);
		instance.setLastMetricsJson("""
				{"gpus":[{"index":0,"name":"t4","util_percent":10,"mem_used_mb":4096,"mem_total_mb":16384,"mem_percent":25,"temperature_c":55}]}
				""");
		CoreInstance saved = coreInstanceRepository.save(instance);
		Long instanceId = saved.getId();
		GenerationJob job = createJob(1, "binder-b");
		GenerationJobMinibatch minibatch = running(job, saved, 0);

		long fromDispatcher = metricsPoller.describeInstances().stream()
				.filter(row -> instanceId.equals(row.id()))
				.findFirst()
				.orElseThrow()
				.inFlight();
		mockMvc.perform(get("/api/cores").header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id==" + instanceId + ")].inFlightTotal", hasItem((int) fromDispatcher)))
				.andExpect(jsonPath("$[?(@.id==" + instanceId + ")].occupancy", hasItem("IN_USE")))
				.andExpect(jsonPath("$[?(@.id==" + instanceId + ")].gpuDataAvailable", hasItem(true)))
				.andExpect(jsonPath("$[?(@.id==" + instanceId + ")].gpus[0].memUsedMb", hasItem(4096.0)));

		mockMvc.perform(get("/api/cores/load-policy").header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cpuEnter").value(90))
				.andExpect(jsonPath("$.gpuMemEnter").value(90))
				.andExpect(jsonPath("$.diskRecover").value(85));

		mockMvc.perform(delete("/api/core-instances/{id}", instanceId)
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isConflict());
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, minibatchRepository.findById(minibatch.getId()).orElseThrow().getStatus());
	}

	@Test
	void historyKeepsNullMegabytesAndRejectsAnUnknownHourWindow() throws Exception {
		CoreInstance instance = instance("hist", 1);
		instance.setLastMetricsJson("{\"gpus\":[]}");
		instance = coreInstanceRepository.save(instance);
		CoreInstanceMetricsHistory old = new CoreInstanceMetricsHistory();
		old.setCoreInstanceId(instance.getId());
		old.setCollectedAt(Instant.now().minusSeconds(30));
		old.setCpuPercent(12.0);
		historyRepository.save(old);

		mockMvc.perform(get("/api/cores").header("Authorization", "Bearer " + adminToken()))
				.andExpect(jsonPath("$[?(@.id==" + instance.getId() + ")].gpuDataAvailable", hasItem(false)));
		mockMvc.perform(get("/api/cores/{id}/metrics/history", instance.getId())
						.param("hours", "1")
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].gpuMemUsedMb").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$[0].cpuPercent").value(12.0));
		mockMvc.perform(get("/api/cores/{id}/metrics/history", instance.getId())
						.param("hours", "2")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	private CoreInstance instance(String name, int max) {
		CoreInstance instance = new CoreInstance();
		instance.setName(name + "-" + UUID.randomUUID());
		instance.setBaseUrl("http://10.8.0." + (int) (Math.random() * 200 + 20) + ":8000");
		instance.setEnabled(true);
		instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
		instance.setStatus(CoreInstance.STATUS_AVAILABLE);
		instance.setMaxConcurrentRuns(max);
		return coreInstanceRepository.save(instance);
	}

	private GenerationJob createJob(int numSeqs, String backboneName) {
		Project project = new Project();
		project.setName("occ-" + UUID.randomUUID());
		project.setTarget("ATOM 1");
		project = projectRepository.save(project);
		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setName(backboneName);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM      1  N   ALA A   1      11.000  12.000  13.000  1.00  0.00           N");
		backbone.setContigs("A1-10");
		backbone = backboneRepository.save(backbone);
		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(backbone.getId());
		request.setTemperature(0.4);
		request.setNumSeqs(numSeqs);
		return generationJobService.createJob(project.getId(), request);
	}

	private GenerationJobMinibatch running(GenerationJob job, CoreInstance instance, int idx) {
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).stream()
				.filter(row -> row.getIdx() == idx)
				.findFirst()
				.orElseGet(() -> {
					GenerationJobMinibatch created = new GenerationJobMinibatch();
					created.setGenerationJobId(job.getId());
					created.setIdx(idx);
					created.setNOffset(idx);
					created.setNumSeqs(1);
					created.setRunId(job.getRunId() + "-extra-" + idx);
					created.setStatus(GenerationJobMinibatch.STATUS_PENDING);
					return minibatchRepository.save(created);
				});
		minibatch.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		minibatch.setCoreInstanceId(instance.getId());
		minibatch.setStartedAt(Instant.parse("2026-10-13T18:00:00Z"));
		return minibatchRepository.save(minibatch);
	}

	private String adminToken() {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		return JWT.create().withSubject("occ-admin").withClaim("roles", "ROLE_ADMIN").sign(Algorithm.HMAC512(secretBytes));
	}
}
