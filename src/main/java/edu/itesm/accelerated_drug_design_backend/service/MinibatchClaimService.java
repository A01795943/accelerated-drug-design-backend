package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MinibatchClaimService {

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final CoreInstanceRepository coreInstanceRepository;

	public MinibatchClaimService(
			GenerationJobMinibatchRepository minibatchRepository,
			CoreInstanceRepository coreInstanceRepository) {
		this.minibatchRepository = minibatchRepository;
		this.coreInstanceRepository = coreInstanceRepository;
	}

	@Transactional
	public boolean claim(Long minibatchId, Long instanceId) {
		GenerationJobMinibatch minibatch = minibatchRepository.findByIdForUpdate(minibatchId).orElse(null);
		if (minibatch == null || !GenerationJobMinibatch.STATUS_PENDING.equals(minibatch.getStatus())) {
			return false;
		}
		minibatch.setStatus(GenerationJobMinibatch.STATUS_RUNNING);
		minibatch.setCoreInstanceId(instanceId);
		minibatch.setStartedAt(Instant.now());
		minibatch.setAttempts(minibatch.getAttempts() + 1);
		minibatch.setError(null);
		minibatchRepository.save(minibatch);
		return true;
	}

	@Transactional
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

	@Transactional
	public void notePostFailure(Long instanceId) {
		CoreInstance instance = coreInstanceRepository.findById(instanceId).orElse(null);
		if (instance == null) {
			return;
		}
		instance.setConsecutiveFailures(instance.getConsecutiveFailures() + 1);
		coreInstanceRepository.save(instance);
	}
}
