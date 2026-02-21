package edu.itesm.accelerated_drug_design_backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints that return fixed placeholder values for "Generar" (generate) actions.
 * Replace with real logic later.
 */
@RestController
@RequestMapping("/api/generate")
public class GenerateController {

	private static final String FIXED_CONTIGS = "20-35/0 A19-127";
	private static final String FIXED_HOTSPOTS = "A54,A56,A58,A66,A113,A115,A123,A124,A125";
	private static final String FIXED_CHAINS_TO_REMOVE = "B";

	@GetMapping("/contigs")
	public ResponseEntity<String> generateContigs() {
		return ResponseEntity.ok(FIXED_CONTIGS);
	}

	@GetMapping("/hotspots")
	public ResponseEntity<String> generateHotspots() {
		return ResponseEntity.ok(FIXED_HOTSPOTS);
	}

	@GetMapping("/chains-to-remove")
	public ResponseEntity<String> generateChainsToRemove() {
		return ResponseEntity.ok(FIXED_CHAINS_TO_REMOVE);
	}
}
