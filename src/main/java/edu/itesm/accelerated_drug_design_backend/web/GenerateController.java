package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;
import edu.itesm.accelerated_drug_design_backend.service.RfdiffusionParamService;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints that return fixed placeholder values for "Generar" (generate) actions.
 * Replace with real logic later.
 */
@RestController
@RequestMapping("/api/generate")
@Tag(name = "Generate", description = "Generate contigs, hotspots, chains-to-remove for a project")
public class GenerateController {

	private static final String FIXED_CONTIGS = "20-35/0 A19-127";
	private static final String FIXED_HOTSPOTS = "A54,A56,A58,A66,A113,A115,A123,A124,A125";
	private static final String FIXED_CHAINS_TO_REMOVE = "B";
	private final boolean enDemo = true;

	private final RfdiffusionParamService service;

	public GenerateController(RfdiffusionParamService service) {
		this.service = service;
	}

	@GetMapping("/contigs")
	public ResponseEntity<String> generateContigs(@RequestParam("projectId") Long projectId) {
		if(enDemo) {
			return ResponseEntity.ok(FIXED_CONTIGS);
		}
		GenerateResult r = service.generateForProject(projectId);
		return ResponseEntity.ok(r.getContig());
	}

	@GetMapping("/hotspots")
	public ResponseEntity<String> generateHotspots(@RequestParam("projectId") Long projectId) {
		if(enDemo) {
			return ResponseEntity.ok(FIXED_HOTSPOTS);
		}
		GenerateResult r = service.generateForProject(projectId);
		return ResponseEntity.ok(r.getHotspots());
	}

	@GetMapping("/chains-to-remove")
	public ResponseEntity<String> generateChainsToRemove(@RequestParam("projectId") Long projectId) {
		if(enDemo) {
			return ResponseEntity.ok(FIXED_CHAINS_TO_REMOVE);
		}
		GenerateResult r = service.generateForProject(projectId);
		return ResponseEntity.ok(r.getChainsToRemoveCsv());
	}

	// Opcional: endpoint único para no recalcular 3 veces
	@GetMapping("/all")
	public ResponseEntity<GenerateResult> generateAll(@RequestParam("projectId") Long projectId) {
		return ResponseEntity.ok(service.generateForProject(projectId));
	}
}
