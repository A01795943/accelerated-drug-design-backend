package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateBackbonesRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.service.BackboneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/backbones")
public class BackboneController {

	private final BackboneService backboneService;

	public BackboneController(BackboneService backboneService) {
		this.backboneService = backboneService;
	}

	@GetMapping
	public ResponseEntity<List<Backbone>> listBackbones(@PathVariable Long projectId) {
		return ResponseEntity.ok(backboneService.findByProjectId(projectId));
	}

	@GetMapping("/status/{runId}")
	public ResponseEntity<List<Backbone>> checkRunStatus(
			@PathVariable Long projectId,
			@PathVariable String runId) {
		List<Backbone> updated = backboneService.checkRunStatus(projectId, runId);
		return ResponseEntity.ok(updated);
	}

	@PostMapping
	public ResponseEntity<List<Backbone>> createBackbones(
			@PathVariable Long projectId,
			@Valid @RequestBody CreateBackbonesRequest request) {
		List<Backbone> created = backboneService.createBackbones(projectId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@DeleteMapping("/{backboneId}")
	public ResponseEntity<Void> deleteBackbone(
			@PathVariable Long projectId,
			@PathVariable Long backboneId) {
		if (backboneService.deleteByProjectIdAndBackboneId(projectId, backboneId)) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}
}
