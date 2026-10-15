package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.CampaignProgressAggregate;
import edu.itesm.accelerated_drug_design_backend.entity.DataGenerationCampaign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DataGenerationCampaignRepository extends JpaRepository<DataGenerationCampaign, Long> {

	List<DataGenerationCampaign> findByStatus(String status);

	@EntityGraph(attributePaths = "project")
	@Query("SELECT c FROM DataGenerationCampaign c WHERE c.id = :id")
	Optional<DataGenerationCampaign> findWithProjectById(@Param("id") Long id);

	Optional<DataGenerationCampaign> findByIdAndProject_Id(Long id, Long projectId);

	List<DataGenerationCampaign> findByProject_IdOrderByCreatedAtDesc(Long projectId);

	@Query("""
			SELECT new edu.itesm.accelerated_drug_design_backend.dto.CampaignProgressAggregate(
			    c.id,
			    COUNT(DISTINCT b.id),
			    COUNT(DISTINCT CASE WHEN b.status = :completedStatus THEN b.id ELSE NULL END),
			    COUNT(DISTINCT t.id),
			    COUNT(DISTINCT CASE WHEN t.status = :completedStatus THEN t.id ELSE NULL END)
			)
			FROM DataGenerationCampaign c
			LEFT JOIN c.backbones b
			LEFT JOIN b.temperatureJobs t
			WHERE c.project.id = :projectId
			GROUP BY c.id
			""")
	List<CampaignProgressAggregate> aggregateProgressByProjectId(
			@Param("projectId") Long projectId,
			@Param("completedStatus") String completedStatus);
}
