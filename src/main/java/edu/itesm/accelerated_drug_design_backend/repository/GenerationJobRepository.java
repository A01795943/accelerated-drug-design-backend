package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.BackboneFilterOption;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobDetailDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem;
import edu.itesm.accelerated_drug_design_backend.dto.JobStatusView;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem(
			j.id, j.runId, j.status, j.error, j.temperature, j.numSeqs, j.totalRecords,
			b.id, b.name, j.createdAt, j.completedAt, j.coreInstanceId, ci.name,
			j.completedMinibatches, j.totalMinibatches
		) FROM GenerationJob j LEFT JOIN j.backbone b LEFT JOIN CoreInstance ci ON ci.id = j.coreInstanceId
		WHERE j.project.id = :projectId AND b.id IN :backboneIds ORDER BY j.id DESC
		""")
	List<GenerationJobListItem> findListItemsByProjectIdAndBackboneIdIn(
			@Param("projectId") Long projectId, @Param("backboneIds") Collection<Long> backboneIds);

	String LIST_ITEM_SELECT = """
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobListItem(
			j.id, j.runId, j.status, j.error, j.temperature, j.numSeqs, j.totalRecords,
			b.id, b.name, j.createdAt, j.completedAt, j.coreInstanceId, ci.name,
			j.completedMinibatches, j.totalMinibatches
		) FROM GenerationJob j LEFT JOIN j.backbone b LEFT JOIN CoreInstance ci ON ci.id = j.coreInstanceId
		""";

	@Query(value = LIST_ITEM_SELECT + " WHERE j.project.id = :projectId",
			countQuery = "SELECT COUNT(j) FROM GenerationJob j WHERE j.project.id = :projectId")
	Page<GenerationJobListItem> pageListItemsByProjectId(@Param("projectId") Long projectId, Pageable pageable);

	@Query(value = LIST_ITEM_SELECT + " WHERE j.project.id = :projectId AND b.id IN :backboneIds",
			countQuery = "SELECT COUNT(j) FROM GenerationJob j WHERE j.project.id = :projectId AND j.backbone.id IN :backboneIds")
	Page<GenerationJobListItem> pageListItemsByProjectIdAndBackboneIdIn(
			@Param("projectId") Long projectId,
			@Param("backboneIds") Collection<Long> backboneIds,
			Pageable pageable);

	long countByProject_Id(Long projectId);

	long countByProject_IdAndBackbone_IdIn(Long projectId, Collection<Long> backboneIds);

	long countByProject_IdAndStatus(Long projectId, String status);

	long countByProject_IdAndStatusAndBackbone_IdIn(Long projectId, String status, Collection<Long> backboneIds);

	@Query("""
		SELECT j.id FROM GenerationJob j
		WHERE j.project.id = :projectId AND j.status = :status
		ORDER BY j.id ASC
		""")
	List<Long> findIdsByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

	@Query("""
		SELECT j.id FROM GenerationJob j
		WHERE j.project.id = :projectId AND j.status = :status AND j.backbone.id IN :backboneIds
		ORDER BY j.id ASC
		""")
	List<Long> findIdsByProjectIdAndStatusAndBackboneIdIn(
			@Param("projectId") Long projectId,
			@Param("status") String status,
			@Param("backboneIds") Collection<Long> backboneIds);

	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.JobStatusView(j.id, j.status)
		FROM GenerationJob j
		WHERE j.project.id = :projectId AND j.id IN :ids
		""")
	List<JobStatusView> findStatusByProjectIdAndIdIn(
			@Param("projectId") Long projectId, @Param("ids") Collection<Long> ids);

	@Query("""
		SELECT DISTINCT new edu.itesm.accelerated_drug_design_backend.dto.BackboneFilterOption(b.id, b.name)
		FROM GenerationJob j JOIN j.backbone b
		WHERE j.project.id = :projectId
		ORDER BY b.id ASC
		""")
	List<BackboneFilterOption> findBackbonesWithJobs(@Param("projectId") Long projectId);

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
			WHERE j.status = :status
			  AND j.totalMinibatches IS NULL
			  AND (j.nextDispatchAt IS NULL OR j.nextDispatchAt <= :now)
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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE GenerationJob j
			SET j.status = :running
			WHERE j.id = :id AND j.status = :queued
			""")
	int markRunningIfStillQueued(
			@Param("id") Long id,
			@Param("queued") String queued,
			@Param("running") String running);
}
