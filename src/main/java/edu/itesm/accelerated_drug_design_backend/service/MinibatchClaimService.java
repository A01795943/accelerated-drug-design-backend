package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MinibatchClaimService {

	private final GenerationJobMinibatchRepository minibatchRepository;

	public MinibatchClaimService(GenerationJobMinibatchRepository minibatchRepository) {
		this.minibatchRepository = minibatchRepository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public boolean adopt(Long minibatchId, Long instanceId) {
		int updated = minibatchRepository.adoptIfPending(
				minibatchId,
				instanceId,
				Instant.now(),
				GenerationJobMinibatch.STATUS_PENDING,
				GenerationJobMinibatch.STATUS_RUNNING);
		return updated == 1;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public boolean claim(Long minibatchId, Long instanceId) {
		int updated = minibatchRepository.claimIfPending(
				minibatchId,
				instanceId,
				Instant.now(),
				GenerationJobMinibatch.STATUS_PENDING,
				GenerationJobMinibatch.STATUS_RUNNING);
		return updated == 1;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
	public void revertUnsentClaim(Long minibatchId) {
		GenerationJobMinibatch minibatch = minibatchRepository.findById(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_RUNNING.equals(minibatch.getStatus())) {
			return;
		}
		minibatch.setStatus(GenerationJobMinibatch.STATUS_PENDING);
		minibatch.setCoreInstanceId(null);
		minibatch.setStartedAt(null);
		minibatch.setAttempts(Math.max(0, minibatch.getAttempts() - 1));
		minibatchRepository.save(minibatch);
	}

}
