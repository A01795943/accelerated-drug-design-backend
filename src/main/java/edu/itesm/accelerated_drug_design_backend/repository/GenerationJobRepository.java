package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

	@Query("SELECT g FROM GenerationJob g LEFT JOIN FETCH g.backbone WHERE g.project.id = :projectId ORDER BY g.id DESC")
	List<GenerationJob> findByProject_IdOrderByIdDesc(@Param("projectId") Long projectId);

	/** List items sin outputCsv, fasta ni bestPdb para no cargar LONGTEXT. */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem(
			j.id, j.runId, j.status, j.error, j.temperature, j.numSeqs, j.totalRecords,
			b.id, b.name, j.createdAt, j.completedAt, j.coreInstanceId, ci.name,
			j.completedMinibatches, j.totalMinibatches
		) FROM GenerationJob j LEFT JOIN j.backbone b LEFT JOIN CoreInstance ci ON ci.id = j.coreInstanceId
		WHERE j.project.id = :projectId ORDER BY j.id DESC
		""")
	List<GenerationJobListItem> findListItemsByProjectId(@Param("projectId") Long projectId);

	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "backbone" })
	@Query("SELECT g FROM GenerationJob g WHERE g.id = :id")
	Optional<GenerationJob> findByIdWithBackbone(@Param("id") Long id);

	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "backbone" })
	Optional<GenerationJob> findByProject_IdAndRunId(Long projectId, String runId);

	/** Detalle para pantalla: solo id, runId, status, error, totalRecords, backbone (sin bestPdb, fasta). */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto(
			j.id, j.runId, j.status, j.error, j.totalRecords, b.id, b.name, j.coreInstanceId, ci.name,
			j.completedMinibatches, j.totalMinibatches
		) FROM GenerationJob j LEFT JOIN j.backbone b LEFT JOIN CoreInstance ci ON ci.id = j.coreInstanceId
		WHERE j.id = :jobId AND j.project.id = :projectId
		""")
	Optional<GenerationJobDetailDto> findDetailByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT j.bestPdb FROM GenerationJob j WHERE j.id = :jobId AND j.project.id = :projectId")
	Optional<String> findBestPdbByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT j.fasta FROM GenerationJob j WHERE j.id = :jobId AND j.project.id = :projectId")
	Optional<String> findFastaByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	boolean existsByCoreInstanceId(Long coreInstanceId);

	@EntityGraph(attributePaths = {"backbone", "project"})
	Optional<GenerationJob> findByRunId(String runId);

	@Query("""
			SELECT j FROM GenerationJob j
			WHERE j.status = :status AND (j.nextDispatchAt IS NULL OR j.nextDispatchAt <= :now)
			ORDER BY j.id ASC
			""")
	@EntityGraph(attributePaths = {"backbone", "project"})
	List<GenerationJob> findQueuedReady(@Param("status") String status, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE GenerationJob j
			SET j.status = :running, j.coreInstanceId = :instanceId, j.error = NULL
			WHERE j.runId = :runId AND j.status = :queued
			""")
	int markRunningIfQueued(
			@Param("runId") String runId,
			@Param("instanceId") Long instanceId,
			@Param("queued") String queued,
			@Param("running") String running);
}
