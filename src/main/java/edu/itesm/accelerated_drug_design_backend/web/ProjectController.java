package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateProjectRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
	public ResponseEntity<List<Project>> listProjects() {
		return ResponseEntity.ok(projectService.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Project> getProject(@PathVariable Long id) {
		return ResponseEntity.ok(projectService.findById(id));
	}

	@PostMapping
	public ResponseEntity<Project> createProject(@Valid @RequestBody CreateProjectRequest request) {
		Project created = projectService.createProject(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}
}
