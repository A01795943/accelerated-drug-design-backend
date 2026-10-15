package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.CreateCampaignResponse;
import edu.itesm.accelerated_drug_design_backend.dto.CreateDataGenerationCampaignRequest;
import edu.itesm.accelerated_drug_design_backend.dto.DataGenerationCampaignDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.DataGenerationCampaignSummaryDto;
import edu.itesm.accelerated_drug_design_backend.service.DataGenerationCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/data-generation-campaigns")
@Tag(name = "Data generation campaigns", description = "Serialized backbone + sequence generation campaigns")
public class DataGenerationCampaignController {

	private final DataGenerationCampaignService campaignService;

	public DataGenerationCampaignController(DataGenerationCampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@PostMapping
	public ResponseEntity<CreateCampaignResponse> createCampaign(
			@PathVariable Long projectId,
			@Valid @RequestBody CreateDataGenerationCampaignRequest request) {
		CreateCampaignResponse response = campaignService.createCampaign(projectId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public ResponseEntity<List<DataGenerationCampaignSummaryDto>> listCampaigns(@PathVariable Long projectId) {
		return ResponseEntity.ok(campaignService.listSummariesForProject(projectId));
	}

	@GetMapping("/{campaignId}")
	public ResponseEntity<DataGenerationCampaignDetailDto> getCampaign(
			@PathVariable Long projectId,
			@PathVariable Long campaignId) {
		return ResponseEntity.ok(campaignService.getCampaignDetail(projectId, campaignId));
	}
}
