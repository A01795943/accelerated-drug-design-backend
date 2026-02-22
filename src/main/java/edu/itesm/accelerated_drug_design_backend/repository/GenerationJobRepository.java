package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

	@Query("SELECT g FROM GenerationJob g LEFT JOIN FETCH g.backbone WHERE g.project.id = :projectId ORDER BY g.id DESC")
	List<GenerationJob> findByProject_IdOrderByIdDesc(@Param("projectId") Long projectId);

	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "backbone" })
	@Query("SELECT g FROM GenerationJob g WHERE g.id = :id")
	Optional<GenerationJob> findByIdWithBackbone(@Param("id") Long id);

	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "backbone" })
	Optional<GenerationJob> findByProject_IdAndRunId(Long projectId, String runId);
}
