package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchAttemptRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
	private MinibatchClaimService claimService;
	@Autowired
	private MinibatchCollector minibatchCollector;

	@Autowired
	private CoreInstanceHealthService healthService;
	@Autowired
	private CoreMetricsPoller metricsPoller;
	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;
	@Autowired
	private GenerationJobMinibatchAttemptRepository attemptRepository;
	@Autowired
	private MinibatchReportService minibatchReportService;
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
		for (CoreInstance instance : coreInstanceRepository.findAll()) {
			metricsPoller.recordReachable(instance.getId());
		}
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		persistFreeInstance("http://10.1.0.2:8000");
		GenerationJob job = createJob(5);
		minibatchDispatcher.dispatchPending();

		assertEquals(GenerationJob.STATUS_QUEUED, job.getStatus());
		List<GenerationJobMinibatch> firstWave = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		assertEquals(2, firstWave.stream().filter(m -> GenerationJobMinibatch.STATUS_RUNNING.equals(m.getStatus())).count());
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());

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
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());

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
	void reassignmentOfEveryMinibatch_leavesJobRunning() {
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		persistFreeInstance("http://10.1.0.3:8000");
		GenerationJob job = createJob(2);
		minibatchDispatcher.dispatchPending();
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());

		GenerationJobMinibatch running = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		coreInstanceRepository.findById(running.getCoreInstanceId()).orElseThrow()
				.setStatus(CoreInstance.STATUS_UNAVAILABLE);
		minibatchCollector.collect();

		List<GenerationJobMinibatch> chunks = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId());
		assertTrue(chunks.stream().allMatch(m -> GenerationJobMinibatch.STATUS_PENDING.equals(m.getStatus())));
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());
	}

	@Test
	void claimPendingIsSingleWinner() {
		GenerationJob job = createJob(1);
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		CoreInstance instance = persistFreeInstance("http://10.1.0.4:8000");

		assertTrue(claimService.claim(minibatch.getId(), instance.getId()));
		assertTrue(!claimService.claim(minibatch.getId(), instance.getId()));

		GenerationJobMinibatch claimed = minibatchRepository.findById(minibatch.getId()).orElseThrow();
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, claimed.getStatus());
		assertEquals(1, claimed.getAttempts());
		assertEquals(instance.getId(), claimed.getCoreInstanceId());
		assertEquals(GenerationJob.STATUS_QUEUED, reloadJob(job.getId()).getStatus());
	}

	@Test
	void statusTimeoutMarksUnavailableAndKeepsAttemptHistory() {
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		persistFreeInstance("http://10.1.0.5:8000");
		GenerationJob job = createJob(2);
		minibatchDispatcher.dispatchPending();
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());

		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(null);
		minibatchCollector.collect();

		GenerationJobMinibatch chunk = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		assertEquals(GenerationJobMinibatch.STATUS_PENDING, chunk.getStatus());
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());
		List<GenerationJobMinibatchAttempt> history = attemptRepository.findByMinibatchIdOrderByAttemptAsc(chunk.getId());
		assertEquals(1, history.size());
		assertEquals(GenerationJobMinibatchAttempt.OUTCOME_REQUEUED, history.get(0).getOutcome());
		assertEquals("core unreachable", history.get(0).getError());
		CoreInstance down = coreInstanceRepository.findById(history.get(0).getCoreInstanceId()).orElseThrow();
		assertEquals(CoreInstance.STATUS_UNAVAILABLE, down.getStatus());

		metricsPoller.apply(down.getId(), Map.of(
				"cpu", Map.of("percent", 10),
				"memory", Map.of("percent", 10),
				"gpus", List.of(),
				"disk", Map.of("percent", 10)));
		assertEquals(CoreInstance.STATUS_AVAILABLE, reload(down.getId()).getStatus());

		minibatchDispatcher.dispatchPending();
		assertEquals(2, attemptRepository.findByMinibatchIdOrderByAttemptAsc(chunk.getId()).size());
		assertEquals(GenerationJob.STATUS_RUNNING, reloadJob(job.getId()).getStatus());

		var report = minibatchReportService.list(job.getProject().getId(), job.getId()).orElseThrow();
		assertEquals(0, report.minibatches().get(0).nOffset());
		assertEquals(1, report.minibatches().get(0).sequenceEnd());
		assertEquals(2, report.minibatches().get(0).attemptHistory().size());
		assertEquals(Optional.empty(), minibatchReportService.list(job.getProject().getId() + 9999, job.getId()));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void completedOnCore_isPersistedWithoutCallerTransaction() {
		Object previousSize = ReflectionTestUtils.getField(generationJobService, "minibatchSize");
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 2);
		CoreInstance instance = null;
		GenerationJob job = null;
		try {
			instance = persistFreeInstance("http://10.9.0.8:8000");
			job = createJob(2);
			minibatchDispatcher.dispatchPending();
			when(coreSystem.getMpnnStatus(any(), any())).thenReturn(completedStatus(2));
			when(coreSystem.getMpnnStatusDetail(any(), any(), anyInt())).thenReturn(detail(2));

			minibatchCollector.collect();

			GenerationJobMinibatch chunk = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
			assertEquals(GenerationJobMinibatch.STATUS_COMPLETED, chunk.getStatus());
			assertEquals(1, chunk.getAttempts());
			assertEquals(2, recordRepository.findByGenerationJob_IdOrderByNAsc(job.getId()).size());
			GenerationJob finished = generationJobService.findById(job.getId()).orElseThrow();
			assertEquals(GenerationJob.STATUS_COMPLETED, finished.getStatus());
			assertEquals(1, finished.getCompletedMinibatches());
			assertEquals(1, finished.getTotalMinibatches());
		} finally {
			ReflectionTestUtils.setField(generationJobService, "minibatchSize", previousSize);
			if (job != null && job.getProject() != null) {
				generationJobService.deleteByProjectIdAndJobId(job.getProject().getId(), job.getId());
				if (job.getBackboneId() != null) {
					backboneRepository.deleteById(job.getBackboneId());
				}
				projectRepository.deleteById(job.getProject().getId());
			}
			if (instance != null) {
				coreInstanceRepository.deleteById(instance.getId());
			}
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void completedMinibatch_persistsOneHundredRecords() {
		Object previousSize = ReflectionTestUtils.getField(generationJobService, "minibatchSize");
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 100);
		CoreInstance instance = null;
		GenerationJob job = null;
		try {
			instance = persistFreeInstance("http://10.9.1.1:8000");
			job = createJob(100);
			minibatchDispatcher.dispatchPending();
			when(coreSystem.getMpnnStatus(any(), any())).thenReturn(completedStatus(100));
			when(coreSystem.getMpnnStatusDetail(any(), any(), anyInt())).thenReturn(detailRows(100));

			minibatchCollector.collect();

			GenerationJobMinibatch chunk = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
			assertEquals(GenerationJobMinibatch.STATUS_COMPLETED, chunk.getStatus());
			assertEquals(100, recordRepository.findByGenerationJob_IdOrderByNAsc(job.getId()).size());
			assertEquals(100, recordRepository.findByGenerationJob_IdOrderByNAsc(job.getId()).stream()
					.filter(record -> chunk.getId().equals(record.getMinibatchId()))
					.count());
			assertEquals(GenerationJob.STATUS_COMPLETED, generationJobService.findById(job.getId()).orElseThrow().getStatus());
		} finally {
			ReflectionTestUtils.setField(generationJobService, "minibatchSize", previousSize);
			cleanup(job, instance);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentCollectorHealthAndDispatcher_doNotDeadlock() throws Exception {
		Object previousSize = ReflectionTestUtils.getField(generationJobService, "minibatchSize");
		ReflectionTestUtils.setField(generationJobService, "minibatchSize", 100);
		CoreInstance instance = null;
		GenerationJob job = null;
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			instance = persistFreeInstance("http://10.9.1.2:8000");
			job = createJob(100);
			minibatchDispatcher.dispatchPending();
			when(coreSystem.getMpnnStatus(any(), any())).thenReturn(completedStatus(100));
			when(coreSystem.getMpnnStatusDetail(any(), any(), anyInt())).thenReturn(detailRows(100));
			Long jobId = job.getId();
			Long instanceId = instance.getId();
			CountDownLatch start = new CountDownLatch(1);
			List<Future<?>> tasks = List.of(
					pool.submit(() -> {
						start.await(5, TimeUnit.SECONDS);
						minibatchCollector.collect();
						return null;
					}),
					pool.submit(() -> {
						start.await(5, TimeUnit.SECONDS);
						for (int i = 0; i < 20; i++) {
							healthService.persistHealth(instanceId, i % 2 == 0 ? CoreInstance.HEALTH_UP : CoreInstance.HEALTH_DOWN);
						}
						return null;
					}),
					pool.submit(() -> {
						start.await(5, TimeUnit.SECONDS);
						for (int i = 0; i < 20; i++) {
							generationJobService.markRunningIfStillQueued(jobId);
						}
						return null;
					}));
			start.countDown();
			for (Future<?> task : tasks) {
				task.get(30, TimeUnit.SECONDS);
			}
			GenerationJobMinibatch chunk = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(jobId).get(0);
			assertEquals(GenerationJobMinibatch.STATUS_COMPLETED, chunk.getStatus());
			assertEquals(100, recordRepository.countByGenerationJob_Id(jobId));
		} finally {
			pool.shutdownNow();
			ReflectionTestUtils.setField(generationJobService, "minibatchSize", previousSize);
			cleanup(job, instance);
		}
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
		assertEquals(CoreInstance.STATUS_UNAVAILABLE, reload(down.getId()).getStatus());
		assertEquals(1, reload(down.getId()).getConsecutiveFailures());
		assertEquals("metrics unreachable", reload(down.getId()).getStatusReason());

		metricsPoller.apply(down.getId(), calm);
		assertEquals(CoreInstance.STATUS_AVAILABLE, reload(down.getId()).getStatus());
		assertEquals(0, reload(down.getId()).getConsecutiveFailures());

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

	private GenerationJob reloadJob(Long id) {
		return generationJobService.findById(id).orElseThrow();
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
		return detailRows(Math.max(records, 2));
	}

	private static Map<String, Object> detailRows(int records) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (int n = 0; n < records; n++) {
			rows.add(Map.of("n", n, "seq", "AA", "ptm", 0.2, "pdb_content", "ATOM"));
		}
		return Map.of("detail", rows);
	}

	private void cleanup(GenerationJob job, CoreInstance instance) {
		if (job != null && job.getProject() != null) {
			generationJobService.deleteByProjectIdAndJobId(job.getProject().getId(), job.getId());
			if (job.getBackboneId() != null) {
				backboneRepository.deleteById(job.getBackboneId());
			}
			projectRepository.deleteById(job.getProject().getId());
		}
		if (instance != null) {
			coreInstanceRepository.deleteById(instance.getId());
		}
	}
}
