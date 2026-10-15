package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.BackboneListDto;
import edu.itesm.accelerated_drug_design_backend.dto.CreateBackbonesRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class BackboneService {

	private static final Logger log = LoggerFactory.getLogger(BackboneService.class);
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_ERROR = "ERROR";
	private static final String STATUS_RUNNING = "RUNNING";
	private static final String STATUS_QUEUED = Backbone.STATUS_QUEUED;

	private final BackboneRepository backboneRepository;
	private final ProjectService projectService;
	private final CoreSystemInterface coreSystem;
	private final CoreInstanceResolver coreInstanceResolver;
	private final PdbCacheService pdbCache;
	private ObjectProvider<BackboneService> self;

	@Autowired
	void setSelf(ObjectProvider<BackboneService> self) {
		this.self = self;
	}

	public BackboneService(BackboneRepository backboneRepository, ProjectService projectService,
			CoreSystemInterface coreSystem, CoreInstanceResolver coreInstanceResolver, PdbCacheService pdbCache) {
		this.backboneRepository = backboneRepository;
		this.projectService = projectService;
		this.coreSystem = coreSystem;
		this.coreInstanceResolver = coreInstanceResolver;
		this.pdbCache = pdbCache;
	}

	public List<Backbone> findByProjectId(Long projectId) {
		return backboneRepository.findByProject_IdOrderByIdAsc(projectId);
	}

	/** Listado sin structure para pantalla (nombre, id, contigs, hotspots, cadenas a eliminar, etc.). */
	@Transactional(readOnly = true)
	public List<BackboneListDto> findListByProjectId(Long projectId) {
		return backboneRepository.findListByProjectId(projectId);
	}

	@Transactional(readOnly = true)
	public Optional<String> getStructure(Long projectId, Long backboneId) {
		return pdbCache.getBackboneStructure(projectId, backboneId)
				.or(() -> {
					Optional<String> fromDb = backboneRepository.findStructureByProjectIdAndBackboneId(projectId, backboneId);
					fromDb.ifPresent(value -> pdbCache.putBackboneStructure(projectId, backboneId, value));
					return fromDb;
				});
	}

	@Transactional
	public boolean deleteByProjectIdAndBackboneId(Long projectId, Long backboneId) {
		return backboneRepository.findByProject_IdAndId(projectId, backboneId)
				.map(bb -> {
					backboneRepository.delete(bb);
					return true;
				})
				.orElse(false);
	}

	@Transactional
	public List<Backbone> createBackbones(Long projectId, CreateBackbonesRequest request) {
		Project project = projectService.findEntityById(projectId);
		int count = request.getCount() != null ? Math.max(1, request.getCount()) : 1;
		int iterations = request.getIterations() != null ? Math.max(1, request.getIterations()) : 30;
		String contigs = request.getContigs();
		String hotspots = request.getHotspots();
		String chainsToRemove = request.getChainsToRemove();

		String runID = generateRunID();

		List<Backbone> created = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Backbone bb = new Backbone();
			bb.setProject(project);
			bb.setRunID(runID);
			bb.setCoreInstanceId(null);
			bb.setStatus(STATUS_QUEUED);
			bb.setIterations(iterations);
			bb.setContigs(contigs);
			bb.setHotspots(hotspots);
			bb.setChainsToRemove(chainsToRemove);
			created.add(backboneRepository.save(bb));
		}

		return created;
	}

	/**
	 * Check run status with the core system, update backbones in DB, and return updated list.
	 * Core GET returns: { "status": "COMPLETED"|"RUNNING"|"ERROR", "output_pdbs_content": { "output_0": "PDB...", "output_1": "PDB..." }, "error_details": "..." }.
	 */
	public List<Backbone> checkRunStatus(Long projectId, String runId) {
		return checkRunStatus(projectId, runId, null);
	}

	public List<Backbone> checkRunStatus(Long projectId, String runId, CoreInstance instanceOverride) {
		List<Backbone> backbones = backboneRepository.findByProject_IdAndRunIDOrderByIdAsc(projectId, runId);
		if (backbones.isEmpty()) {
			return backbones;
		}
		if (instanceOverride == null && backbones.stream().allMatch(bb -> STATUS_QUEUED.equals(bb.getStatus()))) {
			return backbones;
		}

		CoreInstance instance = instanceOverride != null
				? instanceOverride
				: resolveAssignedInstance(backbones.get(0).getCoreInstanceId());
		if (instance == null) {
			return backbones;
		}

		Map<String, Object> response = coreSystem.getRfdiffusionStatus(instance, runId);
		if (response == null || getString(response, "status") == null || getString(response, "status").isBlank()) {
			return backboneRepository.findByProject_IdAndRunIDOrderByIdAsc(projectId, runId);
		}
		return self.getObject().applyRfdiffusionStatus(projectId, runId, instance.getId(), response);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public List<Backbone> applyRfdiffusionStatus(Long projectId, String runId, Long instanceId, Map<String, Object> response) {
		List<Backbone> backbones = backboneRepository.findByProject_IdAndRunIDOrderByIdAsc(projectId, runId);
		String status = getString(response, "status").toUpperCase().strip();
		for (Backbone bb : backbones) {
			bb.setStatus(status);
			if (bb.getCoreInstanceId() == null && instanceId != null) {
				bb.setCoreInstanceId(instanceId);
			}
		}
		if (STATUS_COMPLETED.equals(status)) {
			Object outputContentObj = response.get("output_pdbs_content");
			if (outputContentObj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> outputPdbsContent = (Map<String, Object>) outputContentObj;
				for (int i = 0; i < backbones.size(); i++) {
					String pdb = getString(outputPdbsContent, "output_" + i);
					if (pdb != null && !pdb.isBlank()) {
						backbones.get(i).setStructure(pdb);
					}
				}
			}
			backboneRepository.saveAll(backbones);
			for (Backbone bb : backbones) {
				pdbCache.evictBackboneStructure(projectId, bb.getId());
			}
		} else if (STATUS_ERROR.equals(status)) {
			String errorDetails = getString(response, "error_details");
			if (errorDetails == null) errorDetails = getString(response, "error");
			if (errorDetails == null) errorDetails = getString(response, "message");
			if (errorDetails == null) errorDetails = "Unknown error";
			for (Backbone bb : backbones) {
				bb.setError(errorDetails);
			}
			backboneRepository.saveAll(backbones);
		} else {
			backboneRepository.saveAll(backbones);
		}
		return backbones;
	}

	private CoreInstance resolveAssignedInstance(Long coreInstanceId) {
		if (coreInstanceId == null) {
			return null;
		}
		return coreInstanceResolver.findById(coreInstanceId).orElse(null);
	}

	@Transactional(readOnly = true)
	public List<String> findQueuedRunIdsReady(Instant now) {
		return backboneRepository.findQueuedRunIdsReady(STATUS_QUEUED, now);
	}

	@Transactional
	public int markRunningIfQueued(String runId, Long instanceId) {
		return backboneRepository.markRunningIfQueued(runId, instanceId, STATUS_QUEUED, STATUS_RUNNING);
	}

	public void triggerQueuedRfdiffusion(String runId, CoreInstance instance) {
		List<Backbone> backbones = backboneRepository.findByRunIDOrderByIdAsc(runId);
		if (backbones.isEmpty()) {
			throw new IllegalStateException("No backbones for runId=" + runId);
		}
		Backbone first = backbones.get(0);
		Project project = first.getProject();
		RfdiffusionRunRequest coreRequest = new RfdiffusionRunRequest();
		coreRequest.setRunId(runId);
		coreRequest.setRunName(runId);
		coreRequest.setPdbContent(project.getTarget() != null ? project.getTarget() : "");
		coreRequest.setContigs(first.getContigs() != null ? first.getContigs() : "");
		coreRequest.setIterations(first.getIterations() != null ? first.getIterations() : 30);
		coreRequest.setNumDesigns(backbones.size());
		coreRequest.setHotspot(first.getHotspots() != null ? first.getHotspots() : "");
		coreRequest.setChainToRemove(first.getChainsToRemove() != null ? first.getChainsToRemove() : "");
		coreSystem.triggerRfdiffusion(instance, coreRequest);
	}

	@Transactional
	public void requeueOrFail(String runId, String error, int maxAttempts, Instant nextDispatchAt) {
		List<Backbone> backbones = backboneRepository.findByRunIDOrderByIdAsc(runId);
		if (backbones.isEmpty()) {
			return;
		}
		int attempts = backbones.get(0).getDispatchAttempts() + 1;
		boolean giveUp = attempts >= maxAttempts;
		for (Backbone bb : backbones) {
			bb.setDispatchAttempts(attempts);
			bb.setCoreInstanceId(null);
			bb.setError(error);
			if (giveUp) {
				bb.setStatus(STATUS_ERROR);
				bb.setNextDispatchAt(null);
			} else {
				bb.setStatus(STATUS_QUEUED);
				bb.setNextDispatchAt(nextDispatchAt);
			}
		}
		backboneRepository.saveAll(backbones);
		if (giveUp) {
			log.warn("Backbone run {} failed after {} dispatch attempts: {}", runId, attempts, error);
		}
	}

	@Transactional(readOnly = true)
	public Optional<Long> findProjectIdByRunId(String runId) {
		return backboneRepository.findByRunIDOrderByIdAsc(runId).stream()
				.findFirst()
				.map(bb -> bb.getProject().getId());
	}

	@Transactional(readOnly = true)
	public List<Backbone> findByRunId(String runId) {
		return backboneRepository.findByRunIDOrderByIdAsc(runId);
	}

	private static String getString(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof String) {
			return (String) v;
		}
		return String.valueOf(v);
	}

	/** Unique runID: timestamp + 4-digit random (e.g. 1730000000000_1234). Same for all backbones in one request. */
	private static String generateRunID() {
		long ts = System.currentTimeMillis();
		int r = new Random().nextInt(10000);
		return ts + "_" + String.format("%04d", r);
	}
}
