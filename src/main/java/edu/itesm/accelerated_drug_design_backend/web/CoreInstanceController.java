package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CoreInstancePatchRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceRequest;
import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceResponse;
import edu.itesm.accelerated_drug_design_backend.service.CoreInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/core-instances")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Core instances", description = "Pool of core instances and availability windows (admin only)")
public class CoreInstanceController {

	private final CoreInstanceService coreInstanceService;

	public CoreInstanceController(CoreInstanceService coreInstanceService) {
		this.coreInstanceService = coreInstanceService;
	}

	@GetMapping
	@Operation(summary = "List core instances")
	public ResponseEntity<List<CoreInstanceResponse>> list() {
		return ResponseEntity.ok(coreInstanceService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get core instance")
	public ResponseEntity<CoreInstanceResponse> get(@PathVariable Long id) {
		return ResponseEntity.ok(coreInstanceService.get(id));
	}

	@PostMapping
	@Operation(summary = "Create core instance")
	public ResponseEntity<CoreInstanceResponse> create(@Valid @RequestBody CoreInstanceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(coreInstanceService.create(request));
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Partially update core instance")
	public ResponseEntity<CoreInstanceResponse> patch(
			@PathVariable Long id,
			@Valid @RequestBody CoreInstancePatchRequest request) {
		return ResponseEntity.ok(coreInstanceService.patch(id, request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete core instance")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		coreInstanceService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
