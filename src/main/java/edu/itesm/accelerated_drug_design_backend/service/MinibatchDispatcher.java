package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MinibatchDispatcher {

	private static final Logger log = LoggerFactory.getLogger(MinibatchDispatcher.class);

	private final GenerationJobMinibatchRepository minibatchRepository;
	private final GenerationJobRepository generationJobRepository;
	private final CoreMetricsPoller metricsPoller;
	private final MinibatchClaimService claimService;
	private final CoreSystemInterface coreSystem;

	public MinibatchDispatcher(
			GenerationJobMinibatchRepository minibatchRepository,
			GenerationJobRepository generationJobRepository,
			CoreMetricsPoller metricsPoller,
			MinibatchClaimService claimService,
			CoreSystemInterface coreSystem) {
		this.minibatchRepository = minibatchRepository;
		this.generationJobRepository = generationJobRepository;
		this.metricsPoller = metricsPoller;
		this.claimService = claimService;
		this.coreSystem = coreSystem;
	}

	@Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:15000}")
	public void dispatchPending() {
		List<GenerationJobMinibatch> pending = minibatchRepository.findByStatusOrderByJobThenIdx(
				GenerationJobMinibatch.STATUS_PENDING);
		for (GenerationJobMinibatch minibatch : pending) {
			try {
				dispatchOne(minibatch);
			} catch (Exception e) {
				log.warn("Minibatch {} dispatch error: {}", minibatch.getId(), e.getMessage());
			}
		}
	}

	private void dispatchOne(GenerationJobMinibatch minibatch) {
		CoreInstance instance = metricsPoller.pickEligible();
		if (instance == null) {
			return;
		}
		GenerationJob job = generationJobRepository.findByIdWithBackbone(minibatch.getGenerationJobId()).orElse(null);
		if (job == null || job.getBackbone() == null) {
			return;
		}
		if (!claimService.claim(minibatch.getId(), instance.getId())) {
			return;
		}
		try {
			coreSystem.triggerMpnn(instance, requestFor(job, minibatch));
			log.info("Dispatched minibatch {} runId={} to instance {}", minibatch.getId(), minibatch.getRunId(), instance.getId());
		} catch (Exception e) {
			log.warn("POST /run/mpnn failed for {} on instance {}: {}", minibatch.getRunId(), instance.getId(), e.getMessage());
			claimService.revertUnsentClaim(minibatch.getId());
			claimService.notePostFailure(instance.getId());
		}
	}

	static int seedFor(long jobId, int idx) {
		int mixed = Long.hashCode(jobId) * 31 + idx;
		return mixed == Integer.MIN_VALUE ? 0 : Math.abs(mixed);
	}

	private static ProteinMpnnRunRequest requestFor(GenerationJob job, GenerationJobMinibatch minibatch) {
		ProteinMpnnRunRequest request = new ProteinMpnnRunRequest();
		request.setRunId(minibatch.getRunId());
		request.setRunName(minibatch.getRunId());
		request.setPdbContent(job.getBackbone().getStructure() != null ? job.getBackbone().getStructure() : "");
		request.setContigs(job.getBackbone().getContigs() != null ? job.getBackbone().getContigs() : "");
		request.setNumSeqs(minibatch.getNumSeqs());
		request.setMpnnSamplingTemp(job.getTemperature() != null ? job.getTemperature() : 0.1);
		request.setUseAlphafold(true);
		request.setSeed(seedFor(job.getId(), minibatch.getIdx()));
		return request;
	}
}
