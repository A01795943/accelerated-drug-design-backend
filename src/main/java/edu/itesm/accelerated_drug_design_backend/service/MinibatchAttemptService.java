package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;

@Service
public class MinibatchAttemptService {

	private final GenerationJobMinibatchAttemptRepository attemptRepository;

	public MinibatchAttemptService(GenerationJobMinibatchAttemptRepository attemptRepository) {
		this.attemptRepository = attemptRepository;
	}

	@Transactional
	public void open(Long minibatchId, int attempt, Long instanceId, Instant startedAt) {
		GenerationJobMinibatchAttempt row = new GenerationJobMinibatchAttempt();
		row.setMinibatchId(minibatchId);
		row.setAttempt(attempt);
		row.setCoreInstanceId(instanceId);
		row.setStartedAt(startedAt != null ? startedAt : Instant.now());
		attemptRepository.save(row);
	}

	@Transactional
	public void finishOpen(Long minibatchId, String outcome, String error) {
		attemptRepository.findByMinibatchIdOrderByAttemptAsc(minibatchId).stream()
				.filter(row -> row.getFinishedAt() == null)
				.max(Comparator.comparingInt(GenerationJobMinibatchAttempt::getAttempt))
				.ifPresent(row -> {
					row.setFinishedAt(Instant.now());
					row.setOutcome(outcome);
					row.setError(error);
					attemptRepository.save(row);
				});
	}
}
