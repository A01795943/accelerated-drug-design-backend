package edu.itesm.accelerated_drug_design_backend.dto;

public class CreateCampaignResponse {

	private final Long campaignId;

	public CreateCampaignResponse(Long campaignId) {
		this.campaignId = campaignId;
	}

	public Long getCampaignId() {
		return campaignId;
	}
}
