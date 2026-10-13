package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneBinderKeyRepository;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BinderKeySettingsIntegrationTest {

	@Autowired
	private SettingsService settingsService;
	@Autowired
	private BinderKeyBackfill backfill;
	@Autowired
	private BinderKeyService binderKeyService;
	@Autowired
	private MinibatchDispatcher dispatcher;
	@Autowired
	private MinibatchCollector collector;
	@Autowired
	private MinibatchPersistenceService persistenceService;
	@Autowired
	private GenerationJobService generationJobService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private BackboneRepository backboneRepository;
	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;
	@Autowired
	private GenerationJobRecordRepository recordRepository;
	@Autowired
	private BackboneBinderKeyRepository keyRepository;
	@Autowired
	private CoreInstanceRepository coreInstanceRepository;
	@MockitoBean
	private CoreSystemInterface coreSystem;

	@Test
	void v12StoresTheAttemptFactorAndSettingsFallBackWhenInvalid() {
		assertEquals(20, settingsService.maxAttemptFactor());
		settingsService.put(SettingsService.MAX_ATTEMPT_FACTOR, "40", "test");
		assertEquals(40, settingsService.maxAttemptFactor());
		settingsService.put(SettingsService.MAX_ATTEMPT_FACTOR, "0", "test");
		assertEquals(20, settingsService.maxAttemptFactor());
		settingsService.put(SettingsService.MAX_ATTEMPT_FACTOR, "nope", "test");
		assertEquals(20, settingsService.maxAttemptFactor());
		settingsService.put(SettingsService.MAX_ATTEMPT_FACTOR, "1001", "test");
		assertEquals(20, settingsService.maxAttemptFactor());
	}

	@Test
	void dispatchSendsMaxAttemptsAndDoesNotExcludeItsOwnKeys() {
		GenerationJob job = createJob(4);
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		Long backboneId = job.getBackboneId();
		binderKeyService.insertIfAbsent(backboneId, "CCCCCCCCCCCCCC", minibatch.getId());
		binderKeyService.insertIfAbsent(backboneId, "DDDDDDDDDDDDDD", null);
		binderKeyService.insertIfAbsent(backboneId, "EEEEEEEEEEEEEE", 9_999L);

		dispatcher.dispatchPending();

		ArgumentCaptor<ProteinMpnnRunRequest> request = ArgumentCaptor.forClass(ProteinMpnnRunRequest.class);
		verify(coreSystem).triggerMpnn(any(), request.capture());
		assertEquals(80, request.getValue().getMaxAttempts());
		List<String> excluded = request.getValue().getExcludeBinderSeqs();
		assertTrue(excluded.contains("DDDDDDDDDDDDDD"));
		assertTrue(excluded.contains("EEEEEEEEEEEEEE"));
		assertFalse(excluded.contains("CCCCCCCCCCCCCC"));
	}

	@Test
	void completedRecordsInsertKeysAndASecondWriteDoesNotDuplicateThem() {
		GenerationJob job = createJob(4);
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		minibatch.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		minibatchRepository.save(minibatch);
		String seq = "A".repeat(72) + "/ " + "c".repeat(14);
		List<Map<String, Object>> details = List.of(Map.of("detail", List.of(Map.of("n", 0, "seq", seq))));

		persistenceService.insertCompletedRecords(minibatch.getId(), null, details);
		persistenceService.insertCompletedRecords(minibatch.getId(), null, details);

		assertEquals(1, recordRepository.countByGenerationJob_Id(job.getId()));
		assertEquals(List.of("C".repeat(14)), keyRepository.findKeys(job.getBackboneId()));
	}

	@Test
	void parallelMinibatchKeyIsKeptAndTheRecordIsStillStored() {
		GenerationJob job = createJob(4);
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		minibatch.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		minibatchRepository.save(minibatch);
		String binder = "F".repeat(14);
		binderKeyService.insertIfAbsent(job.getBackboneId(), binder, 9_999L);
		String seq = "A".repeat(72) + "/" + binder.toLowerCase();

		int already = binderKeyService.replaceForMinibatch(job.getBackboneId(), minibatch.getId(), List.of(seq));

		assertEquals(1, already);
		assertEquals(List.of(binder), keyRepository.findKeys(job.getBackboneId()));
		assertEquals(9_999L, keyRepository.findById(
				new edu.itesm.accelerated_drug_design_backend.entity.BackboneBinderKeyId(job.getBackboneId(), binder))
				.orElseThrow().getMinibatchId());
	}

	@Test
	void backfillCopiesTheBinderTailWithoutReadingPdb() {
		GenerationJob job = createJob(4);
		GenerationJobRecord record = new GenerationJobRecord();
		record.setGenerationJobId(job.getId());
		record.setGenerationJob(job);
		record.setN(0);
		record.setSeq("  " + "b".repeat(72) + "/" + "g".repeat(14) + " \n");
		record.setPdb("ATOM pdb-must-not-be-required");
		recordRepository.save(record);
		settingsService.put(SettingsService.BACKFILL_DONE, "0", "rerun");

		backfill.fillIfNeeded();

		assertTrue(keyRepository.findKeys(job.getBackboneId()).contains("G".repeat(14)));
	}

	@Test
	void unavailableInstanceDoesNotRequeueARunThatIsStillRunning() {
		GenerationJob job = createJob(4);
		GenerationJobMinibatch minibatch = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(job.getId()).get(0);
		CoreInstance instance = coreInstanceRepository.findAll().get(0);
		instance.setStatus(CoreInstance.STATUS_UNAVAILABLE);
		coreInstanceRepository.save(instance);
		minibatch.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		minibatch.setCoreInstanceId(instance.getId());
		minibatch.setAttempts(1);
		minibatchRepository.save(minibatch);
		when(coreSystem.getMpnnStatus(any(), any())).thenReturn(Map.of("status", "RUNNING"));

		collector.collect();

		GenerationJobMinibatch fresh = minibatchRepository.findById(minibatch.getId()).orElseThrow();
		assertEquals(GenerationJobMinibatch.STATUS_RUNNING, fresh.getStatus());
		assertEquals(1, fresh.getAttempts());
	}

	private GenerationJob createJob(int numSeqs) {
		Project project = new Project();
		project.setName("binder-key-" + UUID.randomUUID());
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
		request.setTemperature(0.4);
		request.setNumSeqs(numSeqs);
		return generationJobService.createJob(project.getId(), request);
	}
}
