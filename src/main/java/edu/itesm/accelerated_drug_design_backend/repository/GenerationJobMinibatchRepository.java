package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GenerationJobMinibatchRepository extends JpaRepository<GenerationJobMinibatch, Long> {

	List<GenerationJobMinibatch> findByGenerationJobIdOrderByIdxAsc(Long generationJobId);

	@Query("""
			SELECT m FROM GenerationJobMinibatch m JOIN GenerationJob j ON j.id = m.generationJobId
			WHERE m.status = :status
			ORDER BY j.id ASC, m.idx ASC
			""")
	List<GenerationJobMinibatch> findByStatusOrderByJobThenIdx(@Param("status") String status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE GenerationJobMinibatch m
			SET m.status = :running,
				m.coreInstanceId = :instanceId,
				m.startedAt = COALESCE(m.startedAt, :startedAt)
			WHERE m.id = :id AND m.status = :pending
			""")
	int adoptIfPending(
			@Param("id") Long id,
			@Param("instanceId") Long instanceId,
			@Param("startedAt") Instant startedAt,
			@Param("pending") String pending,
			@Param("running") String running);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE GenerationJobMinibatch m
			SET m.status = :running,
				m.coreInstanceId = :instanceId,
				m.startedAt = :startedAt,
				m.attempts = m.attempts + 1,
				m.error = NULL
			WHERE m.id = :id AND m.status = :pending
			""")
	int claimIfPending(
			@Param("id") Long id,
			@Param("instanceId") Long instanceId,
			@Param("startedAt") Instant startedAt,
			@Param("pending") String pending,
			@Param("running") String running);

	Optional<GenerationJobMinibatch> findByRunId(String runId);

	List<GenerationJobMinibatch> findByRunIdIn(Collection<String> runIds);

	@Query("""
			SELECT m.coreInstanceId AS instanceId,
				m.id AS minibatchId,
				m.generationJobId AS generationJobId,
				m.idx AS idx,
				m.nOffset AS nOffset,
				m.numSeqs AS numSeqs,
				m.startedAt AS startedAt,
				j.temperature AS temperature,
				b.id AS backboneId,
				b.name AS backboneName,
				p.id AS projectId
			FROM GenerationJobMinibatch m
			JOIN GenerationJob j ON j.id = m.generationJobId
			JOIN j.backbone b
			JOIN j.project p
			WHERE m.status = 'RUNNING' AND m.coreInstanceId IN :ids
			""")
	List<RunningOccupancyRow> findRunningOccupancy(@Param("ids") Collection<Long> ids);

	long countByCoreInstanceIdAndStatus(Long coreInstanceId, String status);

	long countByGenerationJobIdAndStatus(Long generationJobId, String status);
}
