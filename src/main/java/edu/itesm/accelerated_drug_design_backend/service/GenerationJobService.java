package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.BackboneFilterOption;
import edu.itesm.accelerated_drug_design_backend.dto.ConsolidateCsvRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ConsolidatedCsvRow;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobPageResponse;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobRecordListDto;
import edu.itesm.accelerated_drug_design_backend.dto.JobStatusView;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RecordsPageResponse;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GenerationJobService {

	private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_ERROR = "ERROR";
	private static final String STATUS_RUNNING = "RUNNING";
	private static final String STATUS_QUEUED = GenerationJob.STATUS_QUEUED;
	private static final int LIST_PAGE_DEFAULT = 10;
	private static final int LIST_PAGE_MAX = 200;
	private static final int CSV_ID_CHUNK = 400;
	private static final int CSV_ROW_BATCH = 500;

	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobRecordRepository generationJobRecordRepository;
	private final GenerationJobMinibatchRepository minibatchRepository;
	private final BackboneRepository backboneRepository;
	private final ProjectService projectService;
	private final CoreSystemInterface coreSystem;
	private final CoreInstanceResolver coreInstanceResolver;
	private final PdbCacheService pdbCache;
	private final EDAService edaService;
	private final int minibatchSize;
	private final BinderKeyService binderKeyService;
	private final SettingsService settingsService;
	private ObjectProvider<GenerationJobService> self;

	@Autowired
	void setSelf(ObjectProvider<GenerationJobService> self) {
		this.self = self;
	}

	public GenerationJobService(GenerationJobRepository generationJobRepository,
			GenerationJobRecordRepository generationJobRecordRepository,
			GenerationJobMinibatchRepository minibatchRepository,
			BackboneRepository backboneRepository,
			ProjectService projectService,
			CoreSystemInterface coreSystem,
			CoreInstanceResolver coreInstanceResolver,
			PdbCacheService pdbCache,
			EDAService edaService,
			BinderKeyService binderKeyService,
			SettingsService settingsService,
			@org.springframework.beans.factory.annotation.Value("${app.generation.minibatch-size:100}") int minibatchSize) {
		this.generationJobRepository = generationJobRepository;
		this.generationJobRecordRepository = generationJobRecordRepository;
		this.minibatchRepository = minibatchRepository;
		this.backboneRepository = backboneRepository;
		this.projectService = projectService;
		this.coreSystem = coreSystem;
		this.coreInstanceResolver = coreInstanceResolver;
		this.pdbCache = pdbCache;
		this.edaService = edaService;
		this.binderKeyService = binderKeyService;
		this.settingsService = settingsService;
		this.minibatchSize = Math.max(1, minibatchSize);
	}

	@Transactional(readOnly = true)
	public List<GenerationJob> findByProjectId(Long projectId) {
		List<GenerationJob> list = generationJobRepository.findByProject_IdOrderByIdDesc(projectId);
		list.forEach(job -> {
			if (job.getBackbone() != null) job.getBackbone().getName();
			if (job.getProject() != null) job.getProject().getId();
		});
		return list;
	}

	/** Returns list items without outputCsv, fasta or bestPdb (query selects only list columns). Enriches with dataset quality for completed jobs. */
	@Transactional(readOnly = true)
	public List<GenerationJobListItem> findListItemsByProjectId(Long projectId) {
		List<GenerationJobListItem> items = generationJobRepository.findListItemsByProjectId(projectId);
		enrichListItems(projectId, items);
		return items;
	}

	/**
	 * One page of generation jobs. An empty {@code backboneIds} means no backbone filter.
	 * Columns stored on the job are sorted in SQL. quality, maxPtm, maxIPtm and elapsed are
	 * computed over the filtered set and then sliced, matching the previous on-screen sort.
	 */
	@Transactional(readOnly = true)
	public GenerationJobPageResponse findListPage(Long projectId, List<Long> backboneIds, int page, int size,
			String sort, String direction) {
		List<Long> backbones = normalizeIds(backboneIds);
		int pageSize = size > 0 ? Math.min(size, LIST_PAGE_MAX) : LIST_PAGE_DEFAULT;
		int pageIndex = Math.max(page, 0);
		boolean asc = "asc".equalsIgnoreCase(direction);
		long consolidatable = countCompleted(projectId, backbones);
		if (isComputedSort(sort)) {
			List<GenerationJobListItem> all = backbones.isEmpty()
					? generationJobRepository.findListItemsByProjectId(projectId)
					: generationJobRepository.findListItemsByProjectIdAndBackboneIdIn(projectId, backbones);
			enrichListItems(projectId, all);
			all.sort((a, b) -> compareListItems(a, b, sort, asc));
			int total = all.size();
			int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
			int from = Math.min(pageIndex * pageSize, total);
			int to = Math.min(from + pageSize, total);
			return new GenerationJobPageResponse(
					new ArrayList<>(all.subList(from, to)), total, totalPages, pageIndex, pageSize, consolidatable);
		}
		Sort order = toSort(sort, asc);
		Page<GenerationJobListItem> result = backbones.isEmpty()
				? generationJobRepository.pageListItemsByProjectId(projectId, PageRequest.of(pageIndex, pageSize, order))
				: generationJobRepository.pageListItemsByProjectIdAndBackboneIdIn(
						projectId, backbones, PageRequest.of(pageIndex, pageSize, order));
		enrichListItems(projectId, result.getContent());
		return new GenerationJobPageResponse(
				result.getContent(),
				result.getTotalElements(),
				result.getTotalPages(),
				pageIndex,
				pageSize,
				consolidatable);
	}

	@Transactional(readOnly = true)
	public List<BackboneFilterOption> findBackbonesWithJobs(Long projectId) {
		return generationJobRepository.findBackbonesWithJobs(projectId);
	}

	/** COMPLETED job ids for the project, optionally restricted to the given backbones. */
	@Transactional(readOnly = true)
	public List<Long> findConsolidatableIds(Long projectId, List<Long> backboneIds) {
		List<Long> backbones = normalizeIds(backboneIds);
		if (backbones.isEmpty()) {
			return generationJobRepository.findIdsByProjectIdAndStatus(projectId, STATUS_COMPLETED);
		}
		return generationJobRepository.findIdsByProjectIdAndStatusAndBackboneIdIn(
				projectId, STATUS_COMPLETED, backbones);
	}

	/**
	 * Jobs the consolidated CSV will include. Explicit ids must exist in the project and be COMPLETED.
	 * {@code selectAllFiltered} resolves every COMPLETED job for the backbone filter
	 * (every backbone in the project when the filter is empty).
	 */
	@Transactional(readOnly = true)
	public List<Long> resolveConsolidatableJobIds(Long projectId, ConsolidateCsvRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("No jobs selected");
		}
		if (request.isSelectAllFiltered()) {
			Set<Long> excluded = new HashSet<>(normalizeIds(request.getExcludedJobIds()));
			List<Long> ids = findConsolidatableIds(projectId, request.getBackboneIds()).stream()
					.filter(id -> !excluded.contains(id))
					.toList();
			if (ids.isEmpty()) {
				throw new IllegalArgumentException("No jobs selected");
			}
			return ids;
		}
		List<Long> requested = normalizeIds(request.getJobIds());
		if (requested.isEmpty()) {
			throw new IllegalArgumentException("No jobs selected");
		}
		Map<Long, String> statusById = new HashMap<>();
		for (List<Long> chunk : partition(requested, CSV_ID_CHUNK)) {
			for (JobStatusView view : generationJobRepository.findStatusByProjectIdAndIdIn(projectId, chunk)) {
				statusById.put(view.id(), view.status());
			}
		}
		for (Long id : requested) {
			String status = statusById.get(id);
			if (status == null) {
				throw new IllegalArgumentException("Job " + id + " does not exist in this project");
			}
			if (!STATUS_COMPLETED.equals(status)) {
				throw new IllegalArgumentException("Job " + id + " is not COMPLETED");
			}
		}
		return requested.stream().distinct().sorted().toList();
	}

	/** Streams the consolidated CSV. Callers must pass ids already validated as COMPLETED in this project. */
	@Transactional(readOnly = true)
	public void writeConsolidatedCsv(Long projectId, List<Long> jobIds, OutputStream outputStream) throws IOException {
		List<Long> sorted = jobIds == null
				? List.of()
				: jobIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
		try {
			ConsolidatedCsvWriter.writeHeader(writer);
			for (List<Long> chunk : partition(sorted, CSV_ID_CHUNK)) {
				long lastJobId = 0L;
				int lastN = -1;
				while (true) {
					List<ConsolidatedCsvRow> batch = generationJobRecordRepository.findConsolidatedBatch(
							projectId, STATUS_COMPLETED, chunk, lastJobId, lastN, PageRequest.of(0, CSV_ROW_BATCH));
					if (batch.isEmpty()) {
						break;
					}
					for (ConsolidatedCsvRow row : batch) {
						ConsolidatedCsvWriter.writeRow(writer, row);
					}
					ConsolidatedCsvRow last = batch.get(batch.size() - 1);
					if (last.jobId() != null) {
						lastJobId = last.jobId();
					}
					if (last.n() != null) {
						lastN = last.n();
					}
					writer.flush();
					if (batch.size() < CSV_ROW_BATCH) {
						break;
					}
				}
			}
		} finally {
			writer.flush();
		}
	}

	public Optional<GenerationJob> findByProjectIdAndRunId(Long projectId, String runId) {
		return generationJobRepository.findByProject_IdAndRunId(projectId, runId);
	}

	@Transactional(readOnly = true)
	public Optional<GenerationJob> findById(Long id) {
		Optional<GenerationJob> opt = generationJobRepository.findByIdWithBackbone(id);
		opt.ifPresent(job -> {
			if (job.getBackbone() != null) job.getBackbone().getName();
			if (job.getProject() != null) job.getProject().getId();
		});
		return opt;
	}

	/** Detalle para pantalla (sin bestPdb, fasta). */
	@Transactional(readOnly = true)
	public Optional<GenerationJobDetailDto> findDetailByProjectIdAndJobId(Long projectId, Long jobId) {
		return generationJobRepository.findDetailByProjectIdAndJobId(projectId, jobId);
	}

	@Transactional(readOnly = true)
	public Optional<String> getBestPdb(Long projectId, Long jobId) {
		return pdbCache.getBestPdb(projectId, jobId)
				.or(() -> {
					Optional<String> fromDb = generationJobRepository.findBestPdbByProjectIdAndJobId(projectId, jobId);
					fromDb.ifPresent(value -> pdbCache.putBestPdb(projectId, jobId, value));
					return fromDb;
				});
	}

	@Transactional(readOnly = true)
	public Optional<String> getFasta(Long projectId, Long jobId) {
		return generationJobRepository.findFastaByProjectIdAndJobId(projectId, jobId);
	}

	@Transactional(readOnly = true)
	public Optional<String> getRecordPdb(Long projectId, Long jobId, Integer n) {
		return pdbCache.getRecordPdb(projectId, jobId, n)
				.or(() -> {
					Optional<String> fromDb = generationJobRecordRepository.findPdbByProjectIdJobIdAndN(projectId, jobId, n);
					fromDb.ifPresent(value -> pdbCache.putRecordPdb(projectId, jobId, n, value));
					return fromDb;
				});
	}

	/** Core has no cancel API; deleting the job drops minibatch rows via FK cascade and leaves the remote run running. */
	@Transactional
	public boolean deleteByProjectIdAndJobId(Long projectId, Long jobId) {
		Optional<GenerationJob> opt = generationJobRepository.findById(jobId);
		if (opt.isEmpty() || !opt.get().getProject().getId().equals(projectId)) {
			return false;
		}
		GenerationJob job = opt.get();
		generationJobRecordRepository.deleteByGenerationJob_Id(job.getId());
		generationJobRepository.delete(job);
		return true;
	}

	@Transactional(readOnly = true)
	public List<GenerationJobRecord> findRecordsByJobId(Long projectId, Long jobId) {
		Optional<GenerationJob> job = generationJobRepository.findById(jobId);
		if (job.isEmpty() || !job.get().getProject().getId().equals(projectId)) {
			return List.of();
		}
		return generationJobRecordRepository.findByGenerationJob_IdOrderByNAsc(jobId);
	}

	/**
	 * Return a batch of list DTOs (no pdb) with pagination info (totalRecords, totalBatches).
	 * batch is 0-based; size is the page size (default 50).
	 */
	@Transactional(readOnly = true)
	public RecordsPageResponse findRecordsByJobIdPaginated(Long projectId, Long jobId, Integer batch, int size) {
		if (generationJobRepository.findDetailByProjectIdAndJobId(projectId, jobId).isEmpty()) {
			return new RecordsPageResponse(List.of(), 0, 0);
		}
		int pageSize = size > 0 ? size : 50;
		int batchIndex = (batch != null && batch >= 0) ? batch : 0;
		var page = generationJobRecordRepository.findRecordsListByJobId(jobId, PageRequest.of(batchIndex, pageSize));
		long totalRecords = page.getTotalElements();
		int totalBatches = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / pageSize);
		return new RecordsPageResponse(page.getContent(), totalRecords, totalBatches);
	}

	/**
	 * Build CSV from all records for the job (list DTOs, no pdb loaded).
	 * Returns empty string if job not found or not in project.
	 */
	@Transactional(readOnly = true)
	public String buildCsvFromRecords(Long projectId, Long jobId) {
		if (generationJobRepository.findDetailByProjectIdAndJobId(projectId, jobId).isEmpty()) {
			return "";
		}
		List<GenerationJobRecordListDto> records = generationJobRecordRepository.findRecordsListByJobIdAll(jobId);
		if (records.isEmpty()) {
			return "mpnn,plddt,ptm,i_ptm,pae,i_pae,rmsd,seq\n";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("mpnn,plddt,ptm,i_ptm,pae,i_pae,rmsd,seq\n");
		for (GenerationJobRecordListDto r : records) {
			sb.append(escapeCsv(r.mpnn()));
			sb.append(',');
			sb.append(r.plddt() != null ? r.plddt() : "");
			sb.append(',');
			sb.append(r.ptm() != null ? r.ptm() : "");
			sb.append(',');
			sb.append(r.iPtm() != null ? r.iPtm() : "");
			sb.append(',');
			sb.append(escapeCsv(r.pae()));
			sb.append(',');
			sb.append(escapeCsv(r.iPae()));
			sb.append(',');
			sb.append(r.rmsd() != null ? r.rmsd() : "");
			sb.append(',');
			sb.append(escapeCsv(r.seq()));
			sb.append('\n');
		}
		return sb.toString();
	}

	private static String escapeCsv(String value) {
		if (value == null) return "";
		if (value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	@Transactional
	public GenerationJob createJob(Long projectId, CreateGenerationJobRequest request) {
		Backbone backbone = backboneRepository.findByProject_IdAndId(projectId, request.getBackboneId())
				.orElseThrow(() -> new IllegalArgumentException("Backbone not found or not in project"));
		if (!STATUS_COMPLETED.equals(backbone.getStatus())) {
			throw new IllegalArgumentException("Backbone must have status COMPLETED");
		}
		if (backbone.getStructure() == null || backbone.getStructure().isBlank()) {
			throw new IllegalArgumentException("Backbone has no structure (PDB)");
		}

		String runId = generateRunID();
		Double temperature = request.getTemperature() != null ? request.getTemperature() : 0.1;
		int numSeqs = request.getNumSeqs() != null ? Math.max(1, request.getNumSeqs()) : 16;
		int chunks = (numSeqs + minibatchSize - 1) / minibatchSize;

		GenerationJob job = new GenerationJob();
		job.setRunId(runId);
		job.setProject(projectService.findEntityById(projectId));
		job.setBackbone(backbone);
		job.setCoreInstanceId(null);
		job.setStatus(STATUS_QUEUED);
		job.setTemperature(temperature);
		job.setNumSeqs(numSeqs);
		job.setCreatedAt(Instant.now());
		job.setTotalMinibatches(chunks);
		job.setCompletedMinibatches(0);
		job = generationJobRepository.save(job);

		for (int idx = 0; idx < chunks; idx++) {
			int offset = idx * minibatchSize;
			int count = Math.min(minibatchSize, numSeqs - offset);
			GenerationJobMinibatch minibatch = new GenerationJobMinibatch();
			minibatch.setGenerationJobId(job.getId());
			minibatch.setIdx(idx);
			minibatch.setNOffset(offset);
			minibatch.setNumSeqs(count);
			minibatch.setRunId(runId + "_m" + idx);
			minibatch.setStatus(GenerationJobMinibatch.STATUS_PENDING);
			minibatch.setAttempts(0);
			minibatchRepository.save(minibatch);
		}
		return job;
	}

	public GenerationJob checkRunStatus(Long projectId, String runId) {
		return checkRunStatus(projectId, runId, null);
	}

	public GenerationJob checkRunStatus(Long projectId, String runId, CoreInstance instanceOverride) {
		GenerationJob job = generationJobRepository.findByProject_IdAndRunId(projectId, runId).orElse(null);
		if (job == null) {
			return null;
		}
		if (job.getTotalMinibatches() != null) {
			return job;
		}
		if (instanceOverride == null && STATUS_QUEUED.equals(job.getStatus())) {
			return job;
		}

		CoreInstance instance = instanceOverride != null
				? instanceOverride
				: resolveAssignedInstance(job.getCoreInstanceId());
		if (instance == null) {
			return job;
		}

		Map<String, Object> response = coreSystem.getMpnnStatus(instance, runId);
		if (response == null) {
			return generationJobRepository.findById(job.getId()).orElse(job);
		}

		String status = getString(response, "status");
		if (status == null || status.isBlank()) {
			return generationJobRepository.findById(job.getId()).orElse(job);
		}
		status = status.toUpperCase().strip();

		List<Map<String, Object>> details = new ArrayList<>();
		if (STATUS_COMPLETED.equals(status)) {
			Map<String, Object> pagination = getMap(response, "pagination");
			Integer totalBatches = pagination != null ? getInteger(pagination, "total_batches") : null;
			if (totalBatches == null) {
				totalBatches = getInteger(response, "total_batches");
			}
			int batches = totalBatches != null && totalBatches > 0 ? totalBatches : 0;
			for (int b = 0; b < batches; b++) {
				Map<String, Object> detailResponse = coreSystem.getMpnnStatusDetail(instance, runId, b);
				if (detailResponse != null) {
					details.add(detailResponse);
				}
			}
		}
		return self.getObject().applyMpnnStatus(job.getId(), instance.getId(), status, response, details);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public GenerationJob applyMpnnStatus(
			Long jobId,
			Long instanceId,
			String status,
			Map<String, Object> response,
			List<Map<String, Object>> details) {
		GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
		if (job == null) {
			return null;
		}
		job.setStatus(status);
		if (job.getCoreInstanceId() == null && instanceId != null) {
			job.setCoreInstanceId(instanceId);
		}
		if (STATUS_COMPLETED.equals(status)) {
			Map<String, Object> summary = getMap(response, "summary");
			String fastaContent = summary != null ? getString(summary, "fasta_content") : null;
			if (fastaContent == null) fastaContent = getString(response, "output_fasta");
			String bestPdbContent = summary != null ? getString(summary, "best_pdb_content") : null;
			if (bestPdbContent == null) bestPdbContent = getString(response, "best_pdb_content");
			Map<String, Object> pagination = getMap(response, "pagination");
			Integer totalRecords = pagination != null ? getInteger(pagination, "total_records") : null;
			if (totalRecords == null) totalRecords = getInteger(response, "total_records");

			job.setFasta(fastaContent);
			job.setBestPdb(bestPdbContent);
			job.setTotalRecords(totalRecords);
			job.setError(null);
			if (job.getCompletedAt() == null) {
				job.setCompletedAt(Instant.now());
			}
			generationJobRecordRepository.deleteByGenerationJob_Id(job.getId());
			List<GenerationJobRecord> rows = new ArrayList<>();
			if (details != null) {
				for (Map<String, Object> detail : details) {
					rows.addAll(buildDetailRecords(job, fastaContent, detail, 0));
				}
			}
			if (!rows.isEmpty()) {
				generationJobRecordRepository.saveAll(rows);
			}
			generationJobRepository.save(job);
			if (job.getProject() != null) {
				pdbCache.evictBestPdb(job.getProject().getId(), job.getId());
				pdbCache.evictRecordPdbsForJob(job.getProject().getId(), job.getId());
			}
		} else if (STATUS_ERROR.equals(status)) {
			String errorDetails = getString(response, "error_details");
			if (errorDetails == null) errorDetails = getString(response, "error");
			if (errorDetails == null) errorDetails = getString(response, "message");
			if (errorDetails == null) errorDetails = "Unknown error";
			job.setError(errorDetails);
			if (job.getCompletedAt() == null) {
				job.setCompletedAt(Instant.now());
			}
			generationJobRepository.save(job);
		} else {
			generationJobRepository.save(job);
		}
		return job;
	}

	private CoreInstance resolveAssignedInstance(Long coreInstanceId) {
		if (coreInstanceId == null) {
			return null;
		}
		return coreInstanceResolver.findById(coreInstanceId).orElse(null);
	}

	@Transactional(readOnly = true)
	public List<GenerationJob> findQueuedReady(Instant now) {
		return generationJobRepository.findQueuedReady(STATUS_QUEUED, now);
	}

	@Transactional
	public int markRunningIfQueued(String runId, Long instanceId) {
		return generationJobRepository.markRunningIfQueued(runId, instanceId, STATUS_QUEUED, STATUS_RUNNING);
	}

	/** Promotes a queued minibatch job after its first chunk is posted. Leaves RUNNING jobs unchanged. */
	public int markRunningIfStillQueued(Long jobId) {
		try {
			return DbLockRetry.call("mark job running", () -> self.getObject().markRunningOnce(jobId));
		} catch (RuntimeException ex) {
			if (DbLockRetry.isLockFailure(ex)) {
				log.warn("Job {} stayed unchanged after lock retries: {}", jobId, ex.getMessage());
				return 0;
			}
			throw ex;
		}
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public int markRunningOnce(Long jobId) {
		return generationJobRepository.markRunningIfStillQueued(jobId, STATUS_QUEUED, STATUS_RUNNING);
	}

	/**
	 * Every binder key stored for the backbone. A retry is not excluded by an
	 * n-range anymore: {@link BinderKeyService#keysToExclude} omits the current
	 * minibatch id instead.
	 */
	@Transactional(readOnly = true)
	public List<String> binderKeysToSkip(Long backboneId, Long jobId, int fromN, int toN) {
		return binderKeyService.keysToExclude(backboneId, null);
	}

	public void triggerQueuedMpnn(String runId, CoreInstance instance) {
		GenerationJob job = generationJobRepository.findByRunId(runId)
				.orElseThrow(() -> new IllegalStateException("No generation job for runId=" + runId));
		Backbone backbone = job.getBackbone();
		ProteinMpnnRunRequest coreRequest = new ProteinMpnnRunRequest();
		coreRequest.setRunId(runId);
		coreRequest.setRunName(runId);
		coreRequest.setPdbContent(backbone.getStructure() != null ? backbone.getStructure() : "");
		coreRequest.setContigs(backbone.getContigs() != null ? backbone.getContigs() : "");
		coreRequest.setNumSeqs(job.getNumSeqs() != null ? job.getNumSeqs() : 16);
		coreRequest.setMpnnSamplingTemp(job.getTemperature() != null ? job.getTemperature() : 0.1);
		coreRequest.setUseAlphafold(true);
		coreRequest.setSeed(MpnnSampling.seedFor(job.getId(), 0));
		int numSeqs = job.getNumSeqs() != null ? job.getNumSeqs() : 16;
		coreRequest.setMaxAttempts(numSeqs * settingsService.maxAttemptFactor());
		coreRequest.setExcludeBinderSeqs(binderKeyService.keysToExclude(backbone.getId(), null));
		coreSystem.triggerMpnn(instance, coreRequest);
	}

	@Transactional
	public void requeueOrFail(String runId, String error, int maxAttempts, Instant nextDispatchAt) {
		GenerationJob job = generationJobRepository.findByRunId(runId).orElse(null);
		if (job == null) {
			return;
		}
		int attempts = job.getDispatchAttempts() + 1;
		job.setDispatchAttempts(attempts);
		job.setCoreInstanceId(null);
		job.setError(error);
		if (attempts >= maxAttempts) {
			job.setStatus(STATUS_ERROR);
			job.setNextDispatchAt(null);
			job.setCompletedAt(Instant.now());
			log.warn("Generation job {} failed after {} dispatch attempts: {}", runId, attempts, error);
		} else {
			job.setStatus(STATUS_QUEUED);
			job.setNextDispatchAt(nextDispatchAt);
		}
		generationJobRepository.save(job);
	}

	@Transactional(readOnly = true)
	public Optional<GenerationJob> findByRunId(String runId) {
		return generationJobRepository.findByRunId(runId);
	}

	@Transactional
	public void saveMinibatchDetails(GenerationJob job, String fastaContent, Map<String, Object> detailResponse, int nOffset) {
		List<GenerationJobRecord> rows = buildDetailRecords(job, fastaContent, detailResponse, nOffset);
		if (!rows.isEmpty()) {
			generationJobRecordRepository.saveAll(rows);
		}
	}

	public List<GenerationJobRecord> buildDetailRecords(
			GenerationJob job, String fastaContent, Map<String, Object> detailResponse, int nOffset) {
		return mapDetailRecords(job, detailResponse, fastaContent, nOffset);
	}

	@SuppressWarnings("unchecked")
	private List<GenerationJobRecord> mapDetailRecords(GenerationJob job, Map<String, Object> batchResponse, String fastaContent, int nOffset) {
		Object recordsObj = batchResponse.get("detail");
		if (recordsObj == null) recordsObj = batchResponse.get("records");
		if (recordsObj == null) recordsObj = batchResponse.get("detail_records");
		if (!(recordsObj instanceof List)) return List.of();
		List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;
		List<GenerationJobRecord> mapped = new ArrayList<>();

		List<FastaEntry> fastaEntries = parseFastaMetrics(fastaContent);
		Map<Integer, FastaEntry> byN = new HashMap<>();
		Map<String, FastaEntry> bySeq = new HashMap<>();
		for (FastaEntry e : fastaEntries) {
			if (e.n >= 0) byN.putIfAbsent(e.n, e);
			String norm = normalizeSeq(e.sequence);
			if (!norm.isEmpty()) bySeq.putIfAbsent(norm, e);
		}

		for (Map<String, Object> row : records) {
			GenerationJobRecord record = mapToRecord(job, row);
			if (record == null) continue;

			FastaEntry match = null;
			Integer n = record.getN();
			String recordSeq = normalizeSeq(record.getSeq());
			if (n != null) {
				FastaEntry byNEntry = byN.get(n);
				if (byNEntry != null && recordSeq.equals(normalizeSeq(byNEntry.sequence))) {
					match = byNEntry;
				}
			}
			if (match == null && recordSeq != null && !recordSeq.isEmpty()) {
				match = bySeq.get(recordSeq);
			}
			if (match != null) {
				applyFastaMetricsToRecord(record, match);
			} else {
				log.warn("No FASTA match for generation_jobs_record jobId={} n={} seq={}", job.getId(), record.getN(), recordSeq != null && recordSeq.length() > 50 ? recordSeq.substring(0, 50) + "..." : recordSeq);
			}
			if (record.getN() != null) {
				record.setN(record.getN() + nOffset);
			}
			mapped.add(record);
		}
		return mapped;
	}

	/** Normalize sequence for comparison: trim, collapse whitespace/newlines. */
	private static String normalizeSeq(String seq) {
		if (seq == null) return "";
		return seq.replaceAll("\\s+", "").trim();
	}

	private static final Pattern N_PATTERN = Pattern.compile("\\bn:(\\d+)\\b");

	/**
	 * Parse FASTA content with metric headers: &gt;design:0 n:0|mpnn:1.565|plddt:0.586|...
	 * Returns one entry per block (header + following sequence lines).
	 */
	private static List<FastaEntry> parseFastaMetrics(String fastaContent) {
		List<FastaEntry> out = new ArrayList<>();
		if (fastaContent == null || fastaContent.isBlank()) return out;
		String[] lines = fastaContent.split("\\r?\\n");
		String currentHeader = null;
		StringBuilder currentSeq = new StringBuilder();
		for (String line : lines) {
			if (line.startsWith(">")) {
				if (currentHeader != null) {
					FastaEntry e = parseFastaHeaderAndSeq(currentHeader, currentSeq.toString());
					if (e != null) out.add(e);
				}
				currentHeader = line.substring(1).trim();
				currentSeq.setLength(0);
			} else if (currentHeader != null) {
				currentSeq.append(line.trim());
			}
		}
		if (currentHeader != null) {
			FastaEntry e = parseFastaHeaderAndSeq(currentHeader, currentSeq.toString());
			if (e != null) out.add(e);
		}
		return out;
	}

	private static FastaEntry parseFastaHeaderAndSeq(String header, String sequence) {
		Integer n = null;
		Matcher m = N_PATTERN.matcher(header);
		if (m.find()) n = Integer.parseInt(m.group(1));

		Map<String, String> kv = new HashMap<>();
		for (String part : header.split("\\|")) {
			int colon = part.indexOf(':');
			if (colon > 0) {
				String k = part.substring(0, colon).trim();
				String v = part.substring(colon + 1).trim();
				kv.put(k, v);
			}
		}
		String mpnn = kv.get("mpnn");
		Double plddt = parseDouble(kv.get("plddt"));
		Double ptm = parseDouble(kv.get("ptm"));
		Double iPtm = parseDouble(kv.get("i_ptm"));
		String pae = kv.get("pae");
		String iPae = kv.get("i_pae");
		Double rmsd = parseDouble(kv.get("rmsd"));
		return new FastaEntry(n != null ? n : -1, sequence, mpnn, plddt, ptm, iPtm, pae, iPae, rmsd);
	}

	private static void applyFastaMetricsToRecord(GenerationJobRecord record, FastaEntry e) {
		if (e.mpnn != null) record.setMpnn(e.mpnn);
		if (e.plddt != null) record.setPlddt(e.plddt);
		if (e.ptm != null) record.setPtm(e.ptm);
		if (e.iPtm != null) record.setIPtm(e.iPtm);
		if (e.pae != null) record.setPae(e.pae);
		if (e.iPae != null) record.setIPae(e.iPae);
		if (e.rmsd != null) record.setRmsd(e.rmsd);
	}

	/** Parsed FASTA block: header metrics + sequence. */
	private static class FastaEntry {
		final int n;
		final String sequence;
		final String mpnn;
		final Double plddt, ptm, iPtm, rmsd;
		final String pae, iPae;

		FastaEntry(int n, String sequence, String mpnn, Double plddt, Double ptm, Double iPtm, String pae, String iPae, Double rmsd) {
			this.n = n;
			this.sequence = sequence;
			this.mpnn = mpnn;
			this.plddt = plddt;
			this.ptm = ptm;
			this.iPtm = iPtm;
			this.pae = pae;
			this.iPae = iPae;
			this.rmsd = rmsd;
		}
	}

	private static GenerationJobRecord mapToRecord(GenerationJob job, Map<String, Object> row) {
		Integer n = getInteger(row, "n");
		if (n == null) {
			Object nObj = row.get("n");
			if (nObj instanceof Number) n = ((Number) nObj).intValue();
		}
		if (n == null) return null;
		GenerationJobRecord record = new GenerationJobRecord();
		record.setGenerationJobId(job.getId());
		record.setN(n);
		record.setGenerationJob(job);
		record.setSeq(getString(row, "seq"));
		record.setMpnn(getString(row, "mpnn"));
		// Prefer pdb_content (full PDB text) from status response, then pdb, then pdb_path
		String pdbVal = getString(row, "pdb_content");
		if (pdbVal == null) pdbVal = getString(row, "pdb");
		if (pdbVal == null) pdbVal = getString(row, "pdb_path");
		record.setPdb(pdbVal);
		record.setPlddt(parseDoubleFromMap(row, "plddt"));
		record.setPtm(parseDoubleFromMap(row, "ptm"));
		record.setIPtm(parseDoubleFromMap(row, "i_ptm"));
		record.setPae(getString(row, "pae"));
		record.setIPae(getStringOrFromMetrics(row, "i_pae"));
		record.setRmsd(parseDoubleFromMap(row, "rmsd"));
		return record;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getMap(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v instanceof Map) return (Map<String, Object>) v;
		return null;
	}

	private static Double parseDoubleFromMap(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v == null) return null;
		if (v instanceof Number) return ((Number) v).doubleValue();
		return parseDouble(v.toString());
	}

	private static Double parseDouble(String s) {
		if (s == null || s.isBlank()) return null;
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String getString(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v == null) return null;
		if (v instanceof String) return (String) v;
		return String.valueOf(v);
	}

	/** Gets string from row, or from row.metrics if present (status API may nest metrics). */
	private static String getStringOrFromMetrics(Map<String, Object> row, String key) {
		String s = getString(row, key);
		if (s != null) return s;
		Map<String, Object> metrics = getMap(row, "metrics");
		if (metrics != null) {
			s = getString(metrics, key);
			if (s != null) return s;
		}
		return null;
	}

	private static Integer getInteger(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v == null) return null;
		if (v instanceof Number) return ((Number) v).intValue();
		try {
			return Integer.parseInt(String.valueOf(v).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void enrichListItems(Long projectId, List<GenerationJobListItem> items) {
		for (GenerationJobListItem item : items) {
			try {
				item.setMaxPtm(generationJobRecordRepository.findMaxPtmByProjectIdAndJobId(projectId, item.getId()));
				item.setMaxIPtm(generationJobRecordRepository.findMaxIPtmByProjectIdAndJobId(projectId, item.getId()));
			} catch (Exception e) {
				log.debug("Could not compute max pTM/iPTM for job {}: {}", item.getId(), e.getMessage());
				item.setMaxPtm(null);
				item.setMaxIPtm(null);
			}
			if (STATUS_COMPLETED.equals(item.getStatus())) {
				try {
					item.setQuality(edaService.getDatasetQuality(projectId, item.getId()));
				} catch (Exception e) {
					log.debug("Could not compute quality for job {}: {}", item.getId(), e.getMessage());
					item.setQuality(null);
				}
			}
		}
	}

	private long countCompleted(Long projectId, List<Long> backbones) {
		if (backbones.isEmpty()) {
			return generationJobRepository.countByProject_IdAndStatus(projectId, STATUS_COMPLETED);
		}
		return generationJobRepository.countByProject_IdAndStatusAndBackbone_IdIn(
				projectId, STATUS_COMPLETED, backbones);
	}

	private static boolean isComputedSort(String sort) {
		return "quality".equals(sort) || "maxPtm".equals(sort) || "maxIPtm".equals(sort) || "elapsed".equals(sort);
	}

	private static Sort toSort(String sort, boolean asc) {
		Sort.Direction direction = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
		String expression = switch (sort == null ? "" : sort) {
			case "runId" -> "j.runId";
			case "backbone" -> "b.name";
			case "temperature" -> "j.temperature";
			case "numSeqs" -> "j.numSeqs";
			case "createdAt" -> "j.createdAt";
			case "completedAt" -> "j.completedAt";
			default -> "j.id";
		};
		return JpaSort.unsafe(direction, expression);
	}

	/** Nulls stay last, independent of direction, same as the job table. */
	private static int compareListItems(GenerationJobListItem a, GenerationJobListItem b, String sort, boolean asc) {
		int dir = asc ? 1 : -1;
		return switch (sort) {
			case "quality" -> compareNullable(a.getQuality(), b.getQuality(), dir);
			case "maxPtm" -> compareNullable(a.getMaxPtm(), b.getMaxPtm(), dir);
			case "maxIPtm" -> compareNullable(a.getMaxIPtm(), b.getMaxIPtm(), dir);
			case "elapsed" -> compareNullable(elapsedMillis(a), elapsedMillis(b), dir);
			default -> 0;
		};
	}

	private static int compareNullable(Double a, Double b, int dir) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		int cmp = Double.compare(a, b);
		if (cmp == 0) {
			return 0;
		}
		return cmp < 0 ? -dir : dir;
	}

	private static Double elapsedMillis(GenerationJobListItem job) {
		if (job.getCreatedAt() == null || job.getCompletedAt() == null) {
			return null;
		}
		long created = job.getCreatedAt().toEpochMilli();
		long completed = job.getCompletedAt().toEpochMilli();
		if (completed < created) {
			return null;
		}
		return (double) (completed - created);
	}

	private static List<Long> normalizeIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return ids.stream().filter(Objects::nonNull).distinct().toList();
	}

	private static <T> List<List<T>> partition(List<T> items, int size) {
		if (items.isEmpty()) {
			return List.of();
		}
		List<List<T>> parts = new ArrayList<>();
		for (int i = 0; i < items.size(); i += size) {
			parts.add(new ArrayList<>(items.subList(i, Math.min(i + size, items.size()))));
		}
		return parts;
	}

	private static String generateRunID() {
		long ts = System.currentTimeMillis();
		int r = new Random().nextInt(10000);
		return ts + "_" + String.format("%04d", r);
	}
}
