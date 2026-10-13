package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM GenerationJobMinibatch m WHERE m.id = :id")
	Optional<GenerationJobMinibatch> findByIdForUpdate(@Param("id") Long id);

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
