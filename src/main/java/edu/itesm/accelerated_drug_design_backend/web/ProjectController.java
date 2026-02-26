package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateProjectRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project CRUD, target and complex PDB")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	@Operation(summary = "List projects", description = "Returns all projects (summary: id, name, description).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<List<ProjectSummaryDto>> listProjects() {
		return ResponseEntity.ok(projectService.findAll());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get project", description = "Returns project summary by ID.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Project not found"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
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
	@Operation(summary = "Create project", description = "Creates a new project with optional target/complex PDB (from URL or body).")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = Project.class))),
			@ApiResponse(responseCode = "400", description = "Invalid request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<Project> createProject(@Valid @RequestBody CreateProjectRequest request) {
		Project created = projectService.createProject(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}
}
