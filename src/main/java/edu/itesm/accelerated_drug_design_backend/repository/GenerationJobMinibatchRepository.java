package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	long countByCoreInstanceIdAndStatus(Long coreInstanceId, String status);

	long countByGenerationJobIdAndStatus(Long generationJobId, String status);
}
