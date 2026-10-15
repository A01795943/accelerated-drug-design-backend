package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One transaction per minibatch write. Called from {@link MinibatchCollector}
 * so the scheduler's self-invocation cannot skip the transaction proxy.
 */
@Service
public class MinibatchPersistenceService {

	private static final Logger log = LoggerFactory.getLogger(MinibatchPersistenceService.class);

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobRecordRepository recordRepository;
	private final GenerationJobService generationJobService;
	private final MinibatchAttemptService attemptService;
	private final PdbCacheService pdbCache;
	private final BinderKeyService binderKeyService;
	private final int maxAttempts;
	private ObjectProvider<MinibatchPersistenceService> self;

	@Autowired
	void setSelf(ObjectProvider<MinibatchPersistenceService> self) {
		this.self = self;
	}

	public MinibatchPersistenceService(
			GenerationJobMinibatchRepository minibatchRepository,
			GenerationJobRepository generationJobRepository,
			GenerationJobRecordRepository recordRepository,
			GenerationJobService generationJobService,
			MinibatchAttemptService attemptService,
			PdbCacheService pdbCache,
			BinderKeyService binderKeyService,
			@Value("${app.generation.max-attempts:3}") int maxAttempts) {
		this.minibatchRepository = minibatchRepository;
		this.generationJobRepository = generationJobRepository;
		this.recordRepository = recordRepository;
		this.generationJobService = generationJobService;
		this.attemptService = attemptService;
		this.pdbCache = pdbCache;
		this.binderKeyService = binderKeyService;
		this.maxAttempts = Math.max(1, maxAttempts);
	}

	public void persistCompleted(Long minibatchId, String fasta, List<Map<String, Object>> details, Map<String, Object> paramDetails) {
		DbLockRetry.run("insert minibatch records", () -> self.getObject().insertCompletedRecords(minibatchId, fasta, details));
		DbLockRetry.run("finish minibatch", () -> self.getObject().finishCompleted(minibatchId, fasta, paramDetails));
	}

	/**
	 * Deletes and inserts records only. The job row is updated afterwards so the
	 * foreign-key shared locks from these inserts are released before any UPDATE
	 * on generation_jobs.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public void insertCompletedRecords(Long minibatchId, String fasta, List<Map<String, Object>> details) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		GenerationJob job = generationJobRepository.findById(minibatch.getGenerationJobId()).orElse(null);
		if (job == null) {
			return;
		}
		List<GenerationJobRecord> rows = new ArrayList<>();
		if (details != null) {
			for (Map<String, Object> detail : details) {
				rows.addAll(generationJobService.buildDetailRecords(job, fasta, detail, minibatch.getNOffset()));
			}
		}
		for (GenerationJobRecord row : rows) {
			row.setMinibatchId(minibatchId);
			row.setGenerationJob(null);
		}
		recordRepository.deleteByMinibatchId(minibatchId);
		List<Integer> ns = rows.stream().map(GenerationJobRecord::getN).filter(n -> n != null).toList();
		if (!ns.isEmpty()) {
			recordRepository.deleteByJobIdAndNs(job.getId(), ns);
		}
		if (!rows.isEmpty()) {
			recordRepository.saveAll(rows);
		}
		List<String> sequences = rows.stream().map(GenerationJobRecord::getSeq).toList();
		binderKeyService.replaceForMinibatch(job.getBackbone().getId(), minibatchId, sequences);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void finishCompleted(Long minibatchId, String fasta, Map<String, Object> paramDetails) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		minibatch.setFasta(fasta);
		MpnnRunStats.apply(minibatch, paramDetails);
		minibatch.setStatus(GenerationJobMinibatch.STATUS_COMPLETED);
		minibatch.setError(null);
		minibatch.setCompletedAt(Instant.now());
		minibatchRepository.save(minibatch);
		attemptService.finishOpen(minibatch.getId(), GenerationJobMinibatchAttempt.OUTCOME_COMPLETED, null);
		closeJob(minibatch.getGenerationJobId());
	}

	public void markCoreFailure(Long minibatchId, String reason) {
		DbLockRetry.run("mark core failure", () -> self.getObject().markCoreFailureTx(minibatchId, reason));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void markCoreFailureTx(Long minibatchId, String reason) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		boolean requeue = minibatch.getAttempts() < maxAttempts;
		attemptService.finishOpen(
				minibatch.getId(),
				requeue ? GenerationJobMinibatchAttempt.OUTCOME_REQUEUED : GenerationJobMinibatchAttempt.OUTCOME_ERROR,
				reason);
		if (requeue) {
			minibatch.setStatus(GenerationJobMinibatch.STATUS_PENDING);
			minibatch.setCoreInstanceId(null);
			minibatch.setStartedAt(null);
			minibatch.setError(reason);
			log.info("Minibatch {} back to PENDING (attempt {}/{}): {}",
					minibatch.getRunId(), minibatch.getAttempts(), maxAttempts, reason);
		} else {
			minibatch.setStatus(GenerationJobMinibatch.STATUS_ERROR);
			minibatch.setError(reason);
			minibatch.setCompletedAt(Instant.now());
			log.warn("Minibatch {} ERROR after {} attempts: {}", minibatch.getRunId(), minibatch.getAttempts(), reason);
		}
		minibatchRepository.save(minibatch);
		closeJob(minibatch.getGenerationJobId());
	}

	private void closeJob(Long jobId) {
		GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
		if (job == null || job.getTotalMinibatches() == null) {
			return;
		}
		List<GenerationJobMinibatch> all = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(jobId);
		int completed = (int) all.stream()
				.filter(m -> GenerationJobMinibatch.STATUS_COMPLETED.equals(m.getStatus()))
				.count();
		job.setCompletedMinibatches(completed);
		boolean terminal = all.stream().allMatch(m ->
				GenerationJobMinibatch.STATUS_COMPLETED.equals(m.getStatus())
						|| GenerationJobMinibatch.STATUS_ERROR.equals(m.getStatus()));
		if (!terminal) {
			generationJobRepository.save(job);
			return;
		}
		job.setTotalRecords((int) recordRepository.countByGenerationJob_Id(jobId));
		job.setCompletedAt(Instant.now());
		boolean allOk = all.stream().allMatch(m -> GenerationJobMinibatch.STATUS_COMPLETED.equals(m.getStatus()));
		if (allOk) {
			StringBuilder fasta = new StringBuilder();
			for (GenerationJobMinibatch minibatch : all) {
				if (minibatch.getFasta() == null || minibatch.getFasta().isBlank()) {
					continue;
				}
				if (!fasta.isEmpty()) {
					fasta.append('\n');
				}
				fasta.append(minibatch.getFasta().strip());
			}
			job.setFasta(fasta.toString());
			job.setBestPdb(bestPdb(jobId));
			job.setStatus(GenerationJob.STATUS_COMPLETED);
			job.setError(null);
		} else {
			String failed = all.stream()
					.filter(m -> GenerationJobMinibatch.STATUS_ERROR.equals(m.getStatus()))
					.map(m -> Integer.toString(m.getIdx()))
					.reduce((a, b) -> a + "," + b)
					.orElse("?");
			job.setStatus(GenerationJob.STATUS_ERROR);
			job.setError("minibatches failed: " + failed);
		}
		generationJobRepository.save(job);
		if (job.getProject() != null) {
			pdbCache.evictBestPdb(job.getProject().getId(), job.getId());
			pdbCache.evictRecordPdbsForJob(job.getProject().getId(), job.getId());
		}
	}

	private String bestPdb(Long jobId) {
		String best = null;
		double bestPtm = Double.NEGATIVE_INFINITY;
		for (GenerationJobRecord record : recordRepository.findByGenerationJob_IdOrderByNAsc(jobId)) {
			if (record.getPdb() == null || record.getPdb().isBlank()) {
				continue;
			}
			double ptm = record.getPtm() == null ? Double.NEGATIVE_INFINITY : record.getPtm();
			if (best == null || ptm > bestPtm) {
				best = record.getPdb();
				bestPtm = ptm;
			}
		}
		return best;
	}
}
