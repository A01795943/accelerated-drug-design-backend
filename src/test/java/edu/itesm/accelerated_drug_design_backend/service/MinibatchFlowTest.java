package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MinibatchFlowTest {

	@Autowired
	private GenerationJobService generationJobService;
	@Autowired
	private MinibatchDispatcher minibatchDispatcher;
	@Autowired
	private MinibatchCollector minibatchCollector;
	@Autowired
	private CoreMetricsPoller metricsPoller;
	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;
	@Autowired
	private GenerationJobRecordRepository recordRepository;
	@Autowired
	private CoreInstanceRepository coreInstanceRepository;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private BackboneRepository backboneRepository;

	@MockitoBean
	private CoreSystemInterface coreSystem;

	@Test
	void partition_250_100_and_1() {
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 100);
		assertChunks(250, List.of(100, 100, 50), List.of(0, 100, 200));
		assertChunks(100, List.of(100), List.of(0));
		assertChunks(1, List.of(1), List.of(0));
	}

	@Test
	void reassignmentKeepsCompletedRecordsAndContiguousN() {
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		persistFreeInstance("http://10.1.0.2:8000");
		GenerationJob job = createJob(5);
		minibatchDispatcher.dispatchPending();

		List<GenerationJobMinibatch> firstWave = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		assertEquals(2, firstWave.stream().filter(m -> GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus())).count());

		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(completedStatus(2));
		when(coreSystem.getMpnnStatusDetail(any(), any(), anyInt())).thenAnswer(inv -> detail(2));
		GenerationJobMinibatch completed = firstWave.stream()
				.filter(m -> GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus()))
				.findFirst()
				.orElseThrow();
		GenerationJobMinibatch moved = firstWave.stream()
				.filter(m -> GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus()) && !m.getId().equals(completed.getId()))
				.findFirst()
				.orElseThrow();
		coreInstanceRepository.findById(moved.getCoreInstanceId()).orElseThrow().setStatus(CoreInstance.STATUS_UNAVAILABLE);
		minibatchCollector.collect();

		GenerationJobMinibatch kept = minibatchRepository.findById(completed.getId()).orElseThrow();
		GenerationJobMinibatch movedAgain = minibatchRepository.findById(moved.getId()).orElseThrow();
		assertEquals(GenerationJobMinibatch.STATUS_COMPLETED, kept.getStatus());
		assertEquals(GenerationJobMinibatch.STATUS_PENDING, movedAgain.getStatus());

		minibatchDispatcher.dispatchPending();
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(completedStatus(2));
		minibatchCollector.collect();

		List<Integer> ns = recordRepository.findByGenerationJob_IdOrderByNAsc(job.getId()).stream()
				.map(GenerationJobRecord::getN)
				.toList();
		assertEquals(ns.stream().distinct().count(), ns.size());
		assertTrue(ns.contains(kept.getNOffset()));
	}

	@Test
	void exhaustedAttempts_marksJobErrorAndKeepsPartialRecords() {
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		GenerationJob job = createJob(4);
		List<GenerationJobMinibatch> chunks = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		GenerationJobMinibatch good = chunks.get(0);
		good.setStatus(GenerationJobMinibatch.STATUS_COMPLETED);
		good.setFasta(">design:0 n:0|ptm:0.4\nAA\n");
		minibatchRepository.save(good);
		GenerationJobRecord record = new GenerationJobRecord();
		record.setGenerationJobId(job.getId());
		record.setGenerationJob(job);
		record.setN(0);
		record.setSeq("AA");
		record.setPtm(0.4);
		recordRepository.save(record);

		GenerationJobMinibatch bad = chunks.get(1);
		bad.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		bad.setAttempts(3);
		bad.setCoreInstanceId(coreInstanceRepository.findTopByOrderByIdAsc().orElseThrow().getId());
		minibatchRepository.save(bad);
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(Map.of("status", "ERROR", "error_details", "orphaned: core restarted"));

		minibatchCollector.collect();

		GenerationJob finished = generationJobService.findById(job.getId()).orElseThrow();
		assertEquals(GenerationJob.STATUS_ERROR, finished.getStatus());
		assertTrue(finished.getError().contains("minibatches failed"));
		assertEquals(1, recordRepository.findByGenerationJob_IdOrderByNAsc(job.getId()).size());
	}

	@Test
	void metricsFailureIsIsolatedAndHysteresisRecovers() {
		CoreInstance up = persistFreeInstance("http://10.2.0.1:8000");
		CoreInstance down = persistFreeInstance("http://10.2.0.2:8000");
		Map<String, Object> calm = Map.of(
				"cpu", Map.of("percent", 10),
				"memory", Map.of("percent", 10),
				"gpus", List.of(Map.of("util_percent", 10, "mem_percent", 10)),
				"disk", Map.of("percent", 10),
				"runs", Map.of("running_count", 0),
				"network", Map.of("sent_per_sec", 0, "recv_per_sec", 0));
		metricsPoller.apply(up.getId(), calm);
		metricsPoller.apply(down.getId(), null);
		assertEquals(CoreInstance.STATUS_AVAILABLE, reload(up.getId()).getStatus());
		assertEquals(1, reload(down.getId()).getConsecutiveFailures());

		metricsPoller.apply(down.getId(), null);
		metricsPoller.apply(down.getId(), null);
		assertEquals(CoreInstance.STATUS_UNAVAILABLE, reload(down.getId()).getStatus());
		assertEquals(CoreInstance.STATUS_AVAILABLE, reload(up.getId()).getStatus());

		metricsPoller.apply(up.getId(), Map.of(
				"cpu", Map.of("percent", 96),
				"memory", Map.of("percent", 10),
				"gpus", List.of(),
				"disk", Map.of("percent", 10)));
		assertEquals(CoreInstance.STATUS_OVERLOADED, reload(up.getId()).getStatus());

		metricsPoller.apply(up.getId(), Map.of(
				"cpu", Map.of("percent", 70),
				"memory", Map.of("percent", 10),
				"gpus", List.of(),
				"disk", Map.of("percent", 10)));
		assertEquals(CoreInstance.STATUS_AVAILABLE, reload(up.getId()).getStatus());
		assertEquals(CoreInstance.STATUS_UNAVAILABLE, reload(down.getId()).getStatus());
	}

	private void assertChunks(int numSeqs, List<Integer> counts, List<Integer> offsets) {
		GenerationJob job = createJob(numSeqs);
		List<GenerationJobMinibatch> chunks = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		assertEquals(counts.size(), chunks.size());
		for (int i = 0; i < counts.size(); i++) {
			assertEquals(counts.get(i), chunks.get(i).getNumSeqs());
			assertEquals(offsets.get(i), chunks.get(i).getNOffset());
			assertEquals(job.getRunId() + "_m" + i, chunks.get(i).getRunId());
		}
	}

	private GenerationJob createJob(int numSeqs) {
		Project project = new Project();
		project.setName("mb-" + UUID.randomUUID());
		project.setTarget("ATOM");
		project = projectRepository.save(project);
		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM");
		backbone.setContigs("A1-2");
		backbone = backboneRepository.save(backbone);
		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(backbone.getId());
		request.setTemperature(0.1);
		request.setNumSeqs(numSeqs);
		return generationJobService.createJob(project.getId(), request);
	}

	private CoreInstance persistFreeInstance(String baseUrl) {
		CoreInstance instance = new CoreInstance();
		instance.setName("mb-" + UUID.randomUUID());
		instance.setBaseUrl(baseUrl);
		instance.setEnabled(true);
		instance.setHealthState(CoreInstance.HEALTH_UP);
		instance.setStatus(CoreInstance.STATUS_AVAILABLE);
		instance.setMaxConcurrentRuns(1);
		return coreInstanceRepository.save(instance);
	}

	private CoreInstance reload(Long id) {
		return coreInstanceRepository.findById(id).orElseThrow();
	}

	private static Map<String, Object> completedStatus(int records) {
		return Map.of(
				"status", "COMPLETED",
				"summary", Map.of("fasta_content", ">design:0 n:0|ptm:0.2|i_pae:0.1\nAA\n"),
				"pagination", Map.of("total_batches", 1, "total_records", records));
	}

	private static Map<String, Object> detail(int records) {
		return Map.of("detail", List.of(
				Map.of("n", 0, "seq", "AA", "ptm", 0.2, "pdb_content", "ATOM0"),
				Map.of("n", 1, "seq", "BB", "ptm", 0.3, "pdb_content", "ATOM1")));
	}
}
