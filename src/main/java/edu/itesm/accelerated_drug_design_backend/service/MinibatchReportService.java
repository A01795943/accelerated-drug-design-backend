package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobMinibatchesDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobMinibatchesDto.AttemptDto;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobMinibatchesDto.InstanceRef;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobMinibatchesDto.MinibatchDto;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatchAttempt;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchAttemptRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MinibatchReportService {

	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobMinibatchRepository minibatchRepository;
	private final GenerationJobMinibatchAttemptRepository attemptRepository;
	private final CoreInstanceRepository coreInstanceRepository;

	public MinibatchReportService(
			GenerationJobRepository generationJobRepository,
			GenerationJobMinibatchRepository minibatchRepository,
			GenerationJobMinibatchAttemptRepository attemptRepository,
			CoreInstanceRepository coreInstanceRepository) {
		this.generationJobRepository = generationJobRepository;
		this.minibatchRepository = minibatchRepository;
		this.attemptRepository = attemptRepository;
		this.coreInstanceRepository = coreInstanceRepository;
	}

	@Transactional(readOnly = true)
	public Optional<GenerationJobMinibatchesDto> list(Long projectId, Long jobId) {
		GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
		if (job == null || job.getProject() == null || !projectId.equals(job.getProject().getId())) {
			return Optional.empty();
		}
		List<GenerationJobMinibatch> minibatches = minibatchRepository.findByGenerationJobIdOrderByIdxAsc(jobId);
		List<Long> ids = minibatches.stream().map(GenerationJobMinibatch::getId).toList();
		Map<Long, List<GenerationJobMinibatchAttempt>> attemptsByMinibatch = new HashMap<>();
		if (!ids.isEmpty()) {
			for (GenerationJobMinibatchAttempt attempt : attemptRepository.findByMinibatchIdInOrderByMinibatchIdAscAttemptAsc(ids)) {
				attemptsByMinibatch.computeIfAbsent(attempt.getMinibatchId(), key -> new ArrayList<>()).add(attempt);
			}
		}
		Map<Long, CoreInstance> instances = new HashMap<>();
		for (CoreInstance instance : coreInstanceRepository.findAll()) {
			instances.put(instance.getId(), instance);
		}
		Instant now = Instant.now();
		List<MinibatchDto> rows = new ArrayList<>();
		for (GenerationJobMinibatch minibatch : minibatches) {
			List<AttemptDto> history = new ArrayList<>();
			for (GenerationJobMinibatchAttempt attempt : attemptsByMinibatch.getOrDefault(minibatch.getId(), List.of())) {
				history.add(new AttemptDto(
						attempt.getAttempt(),
						ref(instances, attempt.getCoreInstanceId()),
						attempt.getStartedAt(),
						attempt.getFinishedAt(),
						duration(attempt.getStartedAt(), attempt.getFinishedAt(), now),
						attempt.getOutcome(),
						attempt.getError()));
			}
			rows.add(new MinibatchDto(
					minibatch.getIdx(),
					minibatch.getNOffset(),
					minibatch.getNOffset() + minibatch.getNumSeqs() - 1,
					minibatch.getNumSeqs(),
					minibatch.getStatus(),
					minibatch.getRunId(),
					ref(instances, minibatch.getCoreInstanceId()),
					minibatch.getAttempts(),
					minibatch.getError(),
					minibatch.getStartedAt(),
					minibatch.getCompletedAt(),
					duration(minibatch.getStartedAt(), minibatch.getCompletedAt(), now),
					history));
		}
		return Optional.of(new GenerationJobMinibatchesDto(
				job.getId(),
				job.getStatus(),
				job.getCreatedAt(),
				job.getCompletedAt(),
				job.getCompletedMinibatches(),
				job.getTotalMinibatches(),
				rows));
	}

	private static InstanceRef ref(Map<Long, CoreInstance> instances, Long id) {
		if (id == null) {
			return null;
		}
		CoreInstance instance = instances.get(id);
		if (instance == null) {
			return new InstanceRef(id, null, null);
		}
		return new InstanceRef(instance.getId(), instance.getName(), instance.getBaseUrl());
	}

	private static Long duration(Instant started, Instant finished, Instant now) {
		if (started == null) {
			return null;
		}
		Instant end = finished != null ? finished : now;
		return Math.max(0, Duration.between(started, end).toMillis());
	}
}
