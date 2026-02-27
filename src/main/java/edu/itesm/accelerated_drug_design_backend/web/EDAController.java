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

import java.util.Map;

/**
 * Endpoints de Exploratory Data Analysis (EDA) para métricas de jobs de generación.
 * Expone datos para gráficas de distribución (histograma con min/max) y análisis por bins.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/generation-jobs/{jobId}/eda")
@Tag(name = "EDA", description = "Exploratory Data Analysis: distribution and bins for job metrics (mpnn, plddt, ptm, i_ptm, pae, i_pae, rmsd)")
public class EDAController {

	private final EDAService edaService;

	public EDAController(EDAService edaService) {
		this.edaService = edaService;
	}

	@GetMapping("/dataset-quality")
	@Operation(summary = "Dataset quality", description = "Returns a global quality index (0–1) for the job dataset based on weighted metrics.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<Map<String, Double>> getDatasetQuality(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Generation job ID") @PathVariable Long jobId) {
		double quality = edaService.getDatasetQuality(projectId, jobId);
		return ResponseEntity.ok(Map.of("quality", quality));
	}

	@GetMapping("/descriptive-stats")
	@Operation(summary = "Descriptive statistics", description = "Returns mean, std, min, 25%, 50%, 75%, max, skew and kurtosis for ptm, i_ptm, pae, i_pae, plddt, rmsd, mpnn.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<Map<String, DescriptiveStatsDto>> getDescriptiveStats(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Generation job ID") @PathVariable Long jobId) {
		return ResponseEntity.ok(edaService.getDescriptiveStats(projectId, jobId));
	}

	@GetMapping("/distribution")
	@Operation(summary = "Distribution data", description = "Returns values, min and max for the given metric to build a histogram.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "400", description = "Invalid metric"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<DistributionResponse> getDistribution(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Generation job ID") @PathVariable Long jobId,
			@Parameter(description = "Metric: mpnn, plddt, ptm, i_ptm, pae, i_pae, rmsd") @RequestParam String metric) {
		DistributionResponse response = edaService.getDistribution(projectId, jobId, metric);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/bins")
	@Operation(summary = "Bins analysis", description = "Returns counts and percentages per category for the given metric and custom bins/labels.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success"),
			@ApiResponse(responseCode = "400", description = "Invalid metric or bins/labels"),
			@ApiResponse(responseCode = "404", description = "Job not found"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	public ResponseEntity<BinsResponse> getBins(
			@Parameter(description = "Project ID") @PathVariable Long projectId,
			@Parameter(description = "Generation job ID") @PathVariable Long jobId,
			@Parameter(description = "Metric: mpnn, plddt, ptm, i_ptm, pae, i_pae, rmsd") @RequestParam String metric,
			@Valid @RequestBody BinsRequest request) {
		BinsResponse response = edaService.getBins(projectId, jobId, metric, request);
		return ResponseEntity.ok(response);
	}
}
