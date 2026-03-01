package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.BinsRequest;
import edu.itesm.accelerated_drug_design_backend.dto.BinsResponse;
import edu.itesm.accelerated_drug_design_backend.dto.DescriptiveStatsDto;
import edu.itesm.accelerated_drug_design_backend.dto.DistributionResponse;
import edu.itesm.accelerated_drug_design_backend.service.EDAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de EDA agregada para múltiples jobs de un mismo proyecto.
 * Usa todos los registros de los jobs seleccionados como si fueran un solo dataset.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/generation-jobs/eda")
@Tag(name = "EDA (multi-job)", description = "Exploratory Data Analysis usando múltiples jobs del mismo proyecto")
public class EDAMultiController {

	private final EDAService edaService;

	public EDAMultiController(EDAService edaService) {
		this.edaService = edaService;
	}

	@GetMapping("/descriptive-stats")
	@Operation(summary = "Descriptive statistics (multi-job)", description = "Estadísticas descriptivas (mean, std, percentiles, skew, kurtosis, quality) usando todos los registros de los jobs seleccionados.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<Map<String, DescriptiveStatsDto>> getDescriptiveStatsForJobs(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Comma-separated job IDs, e.g. 1,2,3") @RequestParam List<Long> jobIds) {
		return ResponseEntity.ok(edaService.getDescriptiveStatsForJobs(projectId, jobIds));
	}

	@GetMapping("/distribution")
	@Operation(summary = "Distribution data (multi-job)", description = "Valores agregados, min y max para construir histogramas usando múltiples jobs.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "400", description = "Invalid metric"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<DistributionResponse> getDistributionForJobs(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Métrica: mpnn, plddt, ptm, i_ptm, pae, i_pae, rmsd") @RequestParam String metric,
			@Parameter(description = "Comma-separated job IDs, e.g. 1,2,3") @RequestParam List<Long> jobIds) {
		DistributionResponse response = edaService.getDistributionForJobs(projectId, jobIds, metric);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/bins")
	@Operation(summary = "Bins analysis (multi-job)", description = "Análisis por bins/categorías usando múltiples jobs para una métrica.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "400", description = "Invalid metric or bins/labels"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<BinsResponse> getBinsForJobs(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Métrica: mpnn, plddt, ptm, i_ptm, pae, i_pae, rmsd") @RequestParam String metric,
			@Parameter(description = "Comma-separated job IDs, e.g. 1,2,3") @RequestParam List<Long> jobIds,
			@Valid @RequestBody BinsRequest request) {
		BinsResponse response = edaService.getBinsForJobs(projectId, jobIds, request, metric);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/dataset-quality")
	@Operation(summary = "Dataset quality (multi-job)", description = "Índice de calidad global (0–1) para el dataset combinado de múltiples jobs.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<Map<String, Double>> getDatasetQualityForJobs(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Comma-separated job IDs, e.g. 1,2,3") @RequestParam List<Long> jobIds) {
		double quality = edaService.getDatasetQualityForJobs(projectId, jobIds);
		return ResponseEntity.ok(Map.of("quality", quality));
	}
}

