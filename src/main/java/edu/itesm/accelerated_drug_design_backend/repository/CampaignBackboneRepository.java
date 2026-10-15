package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignBackboneRepository extends JpaRepository<CampaignBackbone, Long> {

	@EntityGraph(attributePaths = {"temperatureJobs"})
	List<CampaignBackbone> findByCampaign_IdOrderBySortOrderAsc(Long campaignId);

	@EntityGraph(attributePaths = {"temperatureJobs"})
	List<CampaignBackbone> findByCampaign_IdAndStatusOrderBySortOrderAsc(Long campaignId, String status);

	Optional<CampaignBackbone> findFirstByCampaign_IdAndStatusOrderBySortOrderAsc(Long campaignId, String status);

	long countByCampaign_Id(Long campaignId);

	long countByCampaign_IdAndStatus(Long campaignId, String status);

	default long countByCampaignId(Long campaignId) {
		return countByCampaign_Id(campaignId);
	}

	default long countByCampaignIdAndStatus(Long campaignId, String status) {
		return countByCampaign_IdAndStatus(campaignId, status);
	}
}
