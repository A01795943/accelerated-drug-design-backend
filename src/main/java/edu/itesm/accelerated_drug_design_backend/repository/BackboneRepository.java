package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.BackboneListDto;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BackboneRepository extends JpaRepository<Backbone, Long> {

	List<Backbone> findByProject_IdOrderByIdAsc(Long projectId);

	/** Listado sin structure (LONGTEXT) para pantalla. */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.BackboneListDto(
			b.id, b.name, b.runID, b.status, b.error, b.contigs, b.hotspots, b.chainsToRemove, b.iterations,
			b.coreInstanceId, ci.name
		) FROM Backbone b LEFT JOIN CoreInstance ci ON ci.id = b.coreInstanceId
		WHERE b.project.id = :projectId ORDER BY b.id ASC
		""")
	List<BackboneListDto> findListByProjectId(@Param("projectId") Long projectId);

	@Query("SELECT b.structure FROM Backbone b WHERE b.project.id = :projectId AND b.id = :backboneId")
	Optional<String> findStructureByProjectIdAndBackboneId(@Param("projectId") Long projectId, @Param("backboneId") Long backboneId);

	List<Backbone> findByProject_IdAndRunIDOrderByIdAsc(Long projectId, String runID);

	Optional<Backbone> findByProject_IdAndId(Long projectId, Long id);

	boolean existsByCoreInstanceId(Long coreInstanceId);

	@EntityGraph(attributePaths = "project")
	List<Backbone> findByRunIDOrderByIdAsc(String runID);

	@Query("""
			SELECT b.runID FROM Backbone b
			WHERE b.status = :status AND (b.nextDispatchAt IS NULL OR b.nextDispatchAt <= :now)
			GROUP BY b.runID
			ORDER BY MIN(b.id)
			""")
	List<String> findQueuedRunIdsReady(@Param("status") String status, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Backbone b
			SET b.status = :running, b.coreInstanceId = :instanceId, b.error = NULL
			WHERE b.runID = :runId AND b.status = :queued
			""")
	int markRunningIfQueued(
			@Param("runId") String runId,
			@Param("instanceId") Long instanceId,
			@Param("queued") String queued,
			@Param("running") String running);
}
