package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.BackboneFilterOption;
import edu.itesm.accelerated_drug_design_backend.dto.ConsolidateCsvRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobMinibatchesDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobPageResponse;
import edu.itesm.accelerated_drug_design_backend.dto.RecordsPageResponse;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.service.GenerationJobService;
import edu.itesm.accelerated_drug_design_backend.service.MinibatchReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/generation-jobs")
@Tag(name = "Generation jobs", description = "ProteinMPNN generation jobs, records, best PDB, FASTA, CSV")
public class GenerationJobController {

	private final GenerationJobService generationJobService;
	private final MinibatchReportService minibatchReportService;

	public GenerationJobController(
			GenerationJobService generationJobService,
			MinibatchReportService minibatchReportService) {
		this.generationJobService = generationJobService;
		this.minibatchReportService = minibatchReportService;
	}

	@GetMapping
	public ResponseEntity<GenerationJobPageResponse> listJobs(
			@PathVariable Long projectId,
			@RequestParam(required = false) List<Long> backboneIds,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "desc") String direction) {
		return ResponseEntity.ok(generationJobService.findListPage(projectId, backboneIds, page, size, sort, direction));
	}

	@GetMapping("/filter-backbones")
	public ResponseEntity<List<BackboneFilterOption>> filterBackbones(@PathVariable Long projectId) {
		return ResponseEntity.ok(generationJobService.findBackbonesWithJobs(projectId));
	}

	@GetMapping("/consolidatable-ids")
	public ResponseEntity<List<Long>> consolidatableIds(
			@PathVariable Long projectId,
			@RequestParam(required = false) List<Long> backboneIds) {
		return ResponseEntity.ok(generationJobService.findConsolidatableIds(projectId, backboneIds));
	}

	@PostMapping(value = "/consolidated-csv", produces = "text/csv")
	public void consolidatedCsv(
			@PathVariable Long projectId,
			@RequestBody ConsolidateCsvRequest request,
			HttpServletResponse response) throws IOException {
		List<Long> jobIds = generationJobService.resolveConsolidatableJobIds(projectId, request);
		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consolidated.csv\"");
		generationJobService.writeConsolidatedCsv(projectId, jobIds, response.getOutputStream());
	}

	@GetMapping("/{jobId}")
	@Operation(summary = "Get job detail", description = "Returns job detail (id, runId, status, error, totalRecords, backbone). Excludes best PDB/FASTA; use dedicated endpoints.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<GenerationJobDetailDto> getJob(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		return generationJobService.findDetailByProjectIdAndJobId(projectId, jobId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/{jobId}/best-pdb", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getBestPdb(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		return generationJobService.getBestPdb(projectId, jobId)
				.map(body -> ResponseEntity.ok().body(body != null ? body : ""))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/{jobId}/fasta", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getFasta(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		return generationJobService.getFasta(projectId, jobId)
				.map(body -> ResponseEntity.ok().body(body != null ? body : ""))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobId}/minibatches")
	@Operation(summary = "List minibatches and attempt history for a generation job")
	public ResponseEntity<GenerationJobMinibatchesDto> listMinibatches(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		return minibatchReportService.list(projectId, jobId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobId}/records")
	public ResponseEntity<RecordsPageResponse> getJobRecords(
			@PathVariable Long projectId,
			@PathVariable Long jobId,
			@RequestParam(required = false) Integer batch,
			@RequestParam(required = false, defaultValue = "50") int size) {
		RecordsPageResponse page = generationJobService.findRecordsByJobIdPaginated(projectId, jobId, batch, size);
		return ResponseEntity.ok(page);
	}

	@GetMapping(value = "/{jobId}/records/{n}/pdb", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getRecordPdb(
			@PathVariable Long projectId,
			@PathVariable Long jobId,
			@PathVariable Integer n) {
		return generationJobService.getRecordPdb(projectId, jobId, n)
				.map(body -> ResponseEntity.ok().body(body != null ? body : ""))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/{jobId}/records/csv", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getJobRecordsCsv(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		String csv = generationJobService.buildCsvFromRecords(projectId, jobId);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-" + jobId + "-records.csv\"");
		return ResponseEntity.ok().headers(headers).body(csv);
	}

	@GetMapping("/status/{runId}")
	public ResponseEntity<GenerationJob> checkRunStatus(
			@PathVariable Long projectId,
			@PathVariable String runId) {
		GenerationJob updated = generationJobService.checkRunStatus(projectId, runId);
		if (updated == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(updated);
	}

	@PostMapping
	public ResponseEntity<GenerationJob> createJob(
			@PathVariable Long projectId,
			@Valid @RequestBody CreateGenerationJobRequest request) {
		GenerationJob created = generationJobService.createJob(projectId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@DeleteMapping("/{jobId}")
	public ResponseEntity<Void> deleteJob(
			@PathVariable Long projectId,
			@PathVariable Long jobId) {
		if (!generationJobService.deleteByProjectIdAndJobId(projectId, jobId)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
