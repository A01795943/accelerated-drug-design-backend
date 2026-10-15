package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GenerationJobMinibatchAttemptRepository extends JpaRepository<GenerationJobMinibatchAttempt, Long> {

	List<GenerationJobMinibatchAttempt> findByMinibatchIdInOrderByMinibatchIdAscAttemptAsc(Collection<Long> minibatchIds);

	List<GenerationJobMinibatchAttempt> findByMinibatchIdOrderByAttemptAsc(Long minibatchId);
}
