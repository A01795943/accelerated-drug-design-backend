package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem;
import edu.itesm.accelerated_drug_design_backend.dto.RecordsPageResponse;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.service.GenerationJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/generation-jobs")
public class GenerationJobController {

	private final GenerationJobService generationJobService;

	public GenerationJobController(GenerationJobService generationJobService) {
		this.generationJobService = generationJobService;
	}

	@GetMapping
	public ResponseEntity<List<GenerationJobListItem>> listJobs(@PathVariable Long projectId) {
		return ResponseEntity.ok(generationJobService.findListItemsByProjectId(projectId));
	}

	@GetMapping("/{jobId}")
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
