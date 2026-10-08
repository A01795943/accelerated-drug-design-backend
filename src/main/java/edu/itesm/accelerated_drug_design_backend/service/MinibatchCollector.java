package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class MinibatchCollector {

	private static final Logger log = LoggerFactory.getLogger(MinibatchCollector.class);

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobRecordRepository recordRepository;
	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreSystemInterface coreSystem;
	private final GenerationJobService generationJobService;
	private final PdbCacheService pdbCache;
	private final int maxAttempts;

	public MinibatchCollector(
			GenerationJobMinibatchRepository minibatchRepository,
			GenerationJobRepository generationJobRepository,
			GenerationJobRecordRepository recordRepository,
			CoreInstanceRepository coreInstanceRepository,
			CoreSystemInterface coreSystem,
			GenerationJobService generationJobService,
			PdbCacheService pdbCache,
			@Value("${app.generation.max-attempts:3}") int maxAttempts) {
		this.minibatchRepository = minibatchRepository;
		this.generationJobRepository = generationJobRepository;
		this.recordRepository = recordRepository;
		this.coreInstanceRepository = coreInstanceRepository;
		this.coreSystem = coreSystem;
		this.generationJobService = generationJobService;
		this.pdbCache = pdbCache;
		this.maxAttempts = Math.max(1, maxAttempts);
	}

	@Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:15000}")
	public void collect() {
		List<GenerationJobMinibatch> running = minibatchRepository.findByStatusOrderByJobThenIdx(
				GenerationJobMinibatch.STATUS_RUNNING);
		for (GenerationJobMinibatch minibatch : running) {
			try {
				collectOne(minibatch.getId());
			} catch (Exception e) {
				log.warn("Collect minibatch {} failed: {}", minibatch.getId(), e.getMessage());
			}
		}
	}

	@Transactional
	public void collectOne(Long minibatchId) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		CoreInstance instance = minibatch.getCoreInstanceId() == null
				? null
				: coreInstanceRepository.findById(minibatch.getCoreInstanceId()).orElse(null);
		if (instance == null || CoreInstance.STATUS_UNAVAILABLE.equals(instance.getStatus())) {
			failOrRequeue(minibatch, instance == null ? "core instance missing" : "core instance UNAVAILABLE");
			return;
		}
		Map<String, Object> response = coreSystem.getMpnnStatus(instance, minibatch.getRunId());
		if (response == null) {
			return;
		}
		String status = text(response, "status");
		if (status == null) {
			return;
		}
		status = status.toUpperCase();
		String errorDetails = text(response, "error_details");
		if ("RUNNING".equals(status)) {
			return;
		}
		if ("ERROR".equals(status) || (errorDetails != null && errorDetails.toLowerCase().contains("orphaned"))) {
			failOrRequeue(minibatch, errorDetails != null ? errorDetails : "core error");
			return;
		}
		if (!"COMPLETED".equals(status)) {
			return;
		}
		String fasta = fastaOf(response);
		int batches = batchesOf(response);
		recordRepository.deleteByJobIdAndNRange(
				minibatch.getGenerationJobId(),
				minibatch.getNOffset(),
				minibatch.getNOffset() + minibatch.getNumSeqs());
		GenerationJob job = generationJobRepository.findById(minibatch.getGenerationJobId()).orElse(null);
		if (job == null) {
			return;
		}
		for (int batch = 0; batch < batches; batch++) {
			Map<String, Object> detail = coreSystem.getMpnnStatusDetail(instance, minibatch.getRunId(), batch);
			if (detail != null) {
				generationJobService.saveMinibatchDetails(job, fasta, detail, minibatch.getNOffset());
			}
		}
		minibatch.setFasta(fasta);
		minibatch.setStatus(GenerationJobMinibatch.STATUS_COMPLETED);
		minibatch.setError(null);
		minibatch.setCompletedAt(Instant.now());
		minibatchRepository.save(minibatch);
		closeJob(job.getId());
	}

	private void failOrRequeue(GenerationJobMinibatch minibatch, String reason) {
		if (minibatch.getAttempts() < maxAttempts) {
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

	@SuppressWarnings("unchecked")
	private static String fastaOf(Map<String, Object> response) {
		Object summary = response.get("summary");
		if (summary instanceof Map<?, ?> map && map.get("fasta_content") != null) {
			return String.valueOf(map.get("fasta_content"));
		}
		Object fasta = response.get("output_fasta");
		return fasta == null ? null : String.valueOf(fasta);
	}

	@SuppressWarnings("unchecked")
	private static int batchesOf(Map<String, Object> response) {
		Object pagination = response.get("pagination");
		if (pagination instanceof Map<?, ?> map && map.get("total_batches") instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		Object top = response.get("total_batches");
		if (top instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		return 0;
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}
}
