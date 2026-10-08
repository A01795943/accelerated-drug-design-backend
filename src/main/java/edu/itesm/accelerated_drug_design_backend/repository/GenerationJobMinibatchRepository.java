package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GenerationJobMinibatchRepository extends JpaRepository<GenerationJobMinibatch, Long> {

	List<GenerationJobMinibatch> findByGenerationJobIdOrderByIdxAsc(Long generationJobId);

	List<GenerationJobMinibatch> findByStatusOrderByGenerationJobIdAscIdxAsc(String status);

	Optional<GenerationJobMinibatch> findByRunId(String runId);

	long countByCoreInstanceIdAndStatus(Long coreInstanceId, String status);

	long countByGenerationJobIdAndStatus(Long generationJobId, String status);
}
