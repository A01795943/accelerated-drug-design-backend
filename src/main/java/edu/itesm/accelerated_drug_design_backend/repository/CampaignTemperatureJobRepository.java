package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.CampaignTemperatureJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignTemperatureJobRepository extends JpaRepository<CampaignTemperatureJob, Long> {

	/**
	 * Temperature jobs for a campaign and status, ordered by backbone sort_order,
	 * then temperature, then id.
	 */
	@Query("""
			SELECT t FROM CampaignTemperatureJob t
			JOIN t.campaignBackbone b
			WHERE b.campaign.id = :campaignId AND t.status = :status
			ORDER BY b.sortOrder ASC, t.temperature ASC, t.id ASC
			""")
	List<CampaignTemperatureJob> findNextByCampaignIdAndStatus(
			@Param("campaignId") Long campaignId,
			@Param("status") String status,
			Pageable pageable);

	/**
	 * PENDING/RUNNING temperature jobs whose parent backbone has the given status
	 * (MPNN+AF must not start until RFdiffusion finished for that physical backbone).
	 */
	@EntityGraph(attributePaths = "campaignBackbone")
	@Query("""
			SELECT t FROM CampaignTemperatureJob t
			JOIN t.campaignBackbone b
			WHERE b.campaign.id = :campaignId
			  AND t.status = :jobStatus
			  AND b.status = :backboneStatus
			ORDER BY b.sortOrder ASC, t.temperature ASC, t.id ASC
			""")
	List<CampaignTemperatureJob> findNextByCampaignIdAndBackboneStatusAndJobStatus(
			@Param("campaignId") Long campaignId,
			@Param("backboneStatus") String backboneStatus,
			@Param("jobStatus") String jobStatus,
			Pageable pageable);

	default Optional<CampaignTemperatureJob> findFirstByCampaignIdAndStatus(Long campaignId, String status) {
		return findNextByCampaignIdAndStatus(campaignId, status, Pageable.ofSize(1)).stream().findFirst();
	}

	default Optional<CampaignTemperatureJob> findFirstPendingForCompletedBackbones(Long campaignId) {
		return findNextByCampaignIdAndBackboneStatusAndJobStatus(
				campaignId,
				edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone.STATUS_COMPLETED,
				CampaignTemperatureJob.STATUS_PENDING,
				Pageable.ofSize(1)).stream().findFirst();
	}

	default Optional<CampaignTemperatureJob> findFirstRunningForCompletedBackbones(Long campaignId) {
		return findNextByCampaignIdAndBackboneStatusAndJobStatus(
				campaignId,
				edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone.STATUS_COMPLETED,
				CampaignTemperatureJob.STATUS_RUNNING,
				Pageable.ofSize(1)).stream().findFirst();
	}

	default List<CampaignTemperatureJob> findAllPendingForCompletedBackbones(Long campaignId) {
		return findNextByCampaignIdAndBackboneStatusAndJobStatus(
				campaignId,
				edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone.STATUS_COMPLETED,
				CampaignTemperatureJob.STATUS_PENDING,
				Pageable.unpaged());
	}

	default List<CampaignTemperatureJob> findAllRunningForCompletedBackbones(Long campaignId) {
		return findNextByCampaignIdAndBackboneStatusAndJobStatus(
				campaignId,
				edu.itesm.accelerated_drug_design_backend.entity.CampaignBackbone.STATUS_COMPLETED,
				CampaignTemperatureJob.STATUS_RUNNING,
				Pageable.unpaged());
	}

	@Query("SELECT COUNT(t) FROM CampaignTemperatureJob t WHERE t.campaignBackbone.campaign.id = :campaignId")
	long countByCampaignId(@Param("campaignId") Long campaignId);

	@Query("SELECT COUNT(t) FROM CampaignTemperatureJob t WHERE t.campaignBackbone.campaign.id = :campaignId AND t.status = :status")
	long countByCampaignIdAndStatus(@Param("campaignId") Long campaignId, @Param("status") String status);
}
