package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RecordsPageResponse;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GenerationJobService {

	private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_ERROR = "ERROR";
	private static final String STATUS_RUNNING = "RUNNING";

	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobRecordRepository generationJobRecordRepository;
	private final BackboneRepository backboneRepository;
	private final ProjectService projectService;
	private final CoreSystemInterface coreSystem;

	public GenerationJobService(GenerationJobRepository generationJobRepository,
			GenerationJobRecordRepository generationJobRecordRepository,
			BackboneRepository backboneRepository,
			ProjectService projectService,
			CoreSystemInterface coreSystem) {
		this.generationJobRepository = generationJobRepository;
		this.generationJobRecordRepository = generationJobRecordRepository;
		this.backboneRepository = backboneRepository;
		this.projectService = projectService;
		this.coreSystem = coreSystem;
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

	public Optional<GenerationJob> findByProjectIdAndRunId(Long projectId, String runId) {
		return generationJobRepository.findByProject_IdAndRunId(projectId, runId);
	}

	@Transactional(readOnly = true)
	public Optional<GenerationJob> findById(Long id) {
		Optional<GenerationJob> opt = generationJobRepository.findById(id);
		opt.ifPresent(job -> {
			if (job.getBackbone() != null) job.getBackbone().getName();
			if (job.getProject() != null) job.getProject().getId();
		});
		return opt;
	}

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
	 * Return a batch of detail records with pagination info (totalRecords, totalBatches).
	 * batch is 0-based; size is the page size (default 50).
	 */
	@Transactional(readOnly = true)
	public RecordsPageResponse findRecordsByJobIdPaginated(Long projectId, Long jobId, Integer batch, int size) {
		Optional<GenerationJob> jobOpt = generationJobRepository.findById(jobId);
		if (jobOpt.isEmpty() || !jobOpt.get().getProject().getId().equals(projectId)) {
			return new RecordsPageResponse(List.of(), 0, 0);
		}
		int pageSize = size > 0 ? size : 50;
		int batchIndex = (batch != null && batch >= 0) ? batch : 0;
		long totalRecords = generationJobRecordRepository.countByGenerationJob_Id(jobId);
		int totalBatches = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / pageSize);
		List<GenerationJobRecord> records = generationJobRecordRepository
				.findByGenerationJob_IdOrderByNAsc(jobId, PageRequest.of(batchIndex, pageSize))
				.getContent();
		return new RecordsPageResponse(records, totalRecords, totalBatches);
	}

	/**
	 * Build CSV from all records for the job (excluding pdb, generation_job_id, n).
	 * Returns empty string if job not found or not in project.
	 */
	@Transactional(readOnly = true)
	public String buildCsvFromRecords(Long projectId, Long jobId) {
		List<GenerationJobRecord> records = findRecordsByJobId(projectId, jobId);
		if (records.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("seq,mpnn,plddt,ptm,i_ptm,pae,i_pae,rmsd\n");
		for (GenerationJobRecord r : records) {
			sb.append(escapeCsv(r.getSeq()));
			sb.append(',');
			sb.append(escapeCsv(r.getMpnn()));
			sb.append(',');
			sb.append(r.getPlddt() != null ? r.getPlddt() : "");
			sb.append(',');
			sb.append(r.getPtm() != null ? r.getPtm() : "");
			sb.append(',');
			sb.append(r.getIPtm() != null ? r.getIPtm() : "");
			sb.append(',');
			sb.append(escapeCsv(r.getPae()));
			sb.append(',');
			sb.append(escapeCsv(r.getIPae()));
			sb.append(',');
			sb.append(r.getRmsd() != null ? r.getRmsd() : "");
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

		GenerationJob job = new GenerationJob();
		job.setRunId(runId);
		job.setProject(projectService.findById(projectId));
		job.setBackbone(backbone);
		job.setStatus(STATUS_RUNNING);
		job.setTemperature(temperature);
		job.setNumSeqs(numSeqs);
		job = generationJobRepository.save(job);

		ProteinMpnnRunRequest coreRequest = new ProteinMpnnRunRequest();
		coreRequest.setRunId(runId);
		coreRequest.setRunName(runId);
		coreRequest.setPdbContent(backbone.getStructure() != null ? backbone.getStructure() : "");
		coreRequest.setContigs(backbone.getContigs() != null ? backbone.getContigs() : "");
		coreRequest.setNumSeqs(numSeqs);
		coreRequest.setMpnnSamplingTemp(temperature);
		coreRequest.setUseAlphafold(true);

		try {
			coreSystem.triggerMpnn(coreRequest);
		} catch (Exception e) {
			String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			job.setStatus(STATUS_ERROR);
			job.setError(errMsg);
			job = generationJobRepository.save(job);
			log.warn("Core MPNN call failed for runId={}: {}", runId, errMsg, e);
		}
		return job;
	}

	@Transactional
	public GenerationJob checkRunStatus(Long projectId, String runId) {
		GenerationJob job = generationJobRepository.findByProject_IdAndRunId(projectId, runId).orElse(null);
		if (job == null) {
			return null;
		}

		Map<String, Object> response = coreSystem.getMpnnStatus(runId, null);
		if (response == null) {
			return job;
		}

		String status = getString(response, "status");
		if (status == null || status.isBlank()) {
			return job;
		}
		status = status.toUpperCase().strip();
		job.setStatus(status);

		if (STATUS_COMPLETED.equals(status)) {
			// Summary: core returns summary.fasta_content, summary.best_pdb_content (and top-level output_fasta)
			Map<String, Object> summary = getMap(response, "summary");
			String fastaContent = summary != null ? getString(summary, "fasta_content") : null;
			if (fastaContent == null) fastaContent = getString(response, "output_fasta");
			String bestPdbContent = summary != null ? getString(summary, "best_pdb_content") : null;
			if (bestPdbContent == null) bestPdbContent = getString(response, "best_pdb_content");
			// Pagination: core returns pagination.total_records, pagination.total_batches
			Map<String, Object> pagination = getMap(response, "pagination");
			Integer totalRecords = pagination != null ? getInteger(pagination, "total_records") : null;
			if (totalRecords == null) totalRecords = getInteger(response, "total_records");
			Integer totalBatches = pagination != null ? getInteger(pagination, "total_batches") : null;
			if (totalBatches == null) totalBatches = getInteger(response, "total_batches");

			job.setFasta(fastaContent);
			job.setBestPdb(bestPdbContent);
			job.setTotalRecords(totalRecords);
			job.setError(null);

			generationJobRecordRepository.deleteByGenerationJob_Id(job.getId());

			// Core returns detail array in the same response (key "detail"); enrich with metrics from FASTA
			saveDetailRecordsFromResponse(job, response, fastaContent);
			generationJobRepository.save(job);
		} else if (STATUS_ERROR.equals(status)) {
			String errorDetails = getString(response, "error_details");
			if (errorDetails == null) errorDetails = getString(response, "error");
			if (errorDetails == null) errorDetails = getString(response, "message");
			if (errorDetails == null) errorDetails = "Unknown error";
			job.setError(errorDetails);
			generationJobRepository.save(job);
		} else {
			generationJobRepository.save(job);
		}
		return job;
	}

	@SuppressWarnings("unchecked")
	private void saveDetailRecordsFromResponse(GenerationJob job, Map<String, Object> batchResponse, String fastaContent) {
		Object recordsObj = batchResponse.get("detail");
		if (recordsObj == null) recordsObj = batchResponse.get("records");
		if (recordsObj == null) recordsObj = batchResponse.get("detail_records");
		if (!(recordsObj instanceof List)) return;
		List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;

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
			generationJobRecordRepository.save(record);
		}
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

	private static String generateRunID() {
		long ts = System.currentTimeMillis();
		int r = new Random().nextInt(10000);
		return ts + "_" + String.format("%04d", r);
	}
}
