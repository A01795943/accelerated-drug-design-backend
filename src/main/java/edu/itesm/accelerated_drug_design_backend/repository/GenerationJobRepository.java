package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

	List<GenerationJob> findByProject_IdOrderByIdDesc(Long projectId);

	Optional<GenerationJob> findByProject_IdAndRunId(Long projectId, String runId);
}
