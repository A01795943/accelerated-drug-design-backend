package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateProjectRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public ResponseEntity<List<ProjectSummaryDto>> listProjects() {
		return ResponseEntity.ok(projectService.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProjectSummaryDto> getProject(@PathVariable Long id) {
		return ResponseEntity.ok(projectService.findById(id));
	}

	@GetMapping(value = "/{id}/target", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getProjectTarget(@PathVariable Long id) {
		String target = projectService.getTarget(id);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.body(target != null ? target : "");
	}

	@GetMapping(value = "/{id}/complex", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getProjectComplex(@PathVariable Long id) {
		String complex = projectService.getComplex(id);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.body(complex != null ? complex : "");
	}

	@PostMapping
	public ResponseEntity<Project> createProject(@Valid @RequestBody CreateProjectRequest request) {
		Project created = projectService.createProject(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}
}
