package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateBackbonesRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class BackboneService {

	private static final Logger log = LoggerFactory.getLogger(BackboneService.class);
	private static final String STATUS_COMPLETED = "COMPLETED";
	private static final String STATUS_ERROR = "ERROR";
	private static final String STATUS_RUNNING = "RUNNING";

	private final BackboneRepository backboneRepository;
	private final ProjectService projectService;
	private final CoreSystemInterface coreSystem;

	public BackboneService(BackboneRepository backboneRepository, ProjectService projectService, CoreSystemInterface coreSystem) {
		this.backboneRepository = backboneRepository;
		this.projectService = projectService;
		this.coreSystem = coreSystem;
	}

	public List<Backbone> findByProjectId(Long projectId) {
		return backboneRepository.findByProject_IdOrderByIdAsc(projectId);
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
		Project project = projectService.findById(projectId);
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
			bb.setStatus(STATUS_RUNNING);
			bb.setIterations(iterations);
			bb.setContigs(contigs);
			bb.setHotspots(hotspots);
			bb.setChainsToRemove(chainsToRemove);
			created.add(backboneRepository.save(bb));
		}

		RfdiffusionRunRequest coreRequest = new RfdiffusionRunRequest();
		coreRequest.setRunId(runID);
		coreRequest.setRunName(runID);
		coreRequest.setPdbContent(project.getTarget() != null ? project.getTarget() : "");
		coreRequest.setContigs(contigs != null ? contigs : "");
		coreRequest.setIterations(iterations);
		coreRequest.setNumDesigns(count);

		try {
			coreSystem.triggerRfdiffusion(coreRequest);
		} catch (Exception e) {
			String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			for (Backbone bb : created) {
				bb.setStatus(STATUS_ERROR);
				bb.setError(errMsg);
			}
			backboneRepository.saveAll(created);
			log.warn("Core rfdiffusion call failed for runId={}: {}", runID, errMsg, e);
		}

		return created;
	}

	/**
	 * Check run status with the core system, update backbones in DB, and return updated list.
	 * Core GET returns: { "status": "COMPLETED"|"RUNNING"|"ERROR", "output_pdbs_content": { "output_0": "PDB...", "output_1": "PDB..." }, "error_details": "..." }.
	 */
	@Transactional
	public List<Backbone> checkRunStatus(Long projectId, String runId) {
		List<Backbone> backbones = backboneRepository.findByProject_IdAndRunIDOrderByIdAsc(projectId, runId);
		if (backbones.isEmpty()) {
			return backbones;
		}

		Map<String, Object> response = coreSystem.getRfdiffusionStatus(runId);
		if (response == null) {
			return backbones;
		}

		String status = getString(response, "status");
		if (status == null || status.isBlank()) {
			return backbones;
		}

		// Normalize status to uppercase for consistency
		status = status.toUpperCase().strip();

		for (Backbone bb : backbones) {
			bb.setStatus(status);
		}

		if (STATUS_COMPLETED.equals(status)) {
			// PDB content is in output_pdbs_content: { "output_0": "ATOM...", "output_1": "ATOM...", ... }
			Object outputContentObj = response.get("output_pdbs_content");
			if (outputContentObj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> outputPdbsContent = (Map<String, Object>) outputContentObj;
				for (int i = 0; i < backbones.size(); i++) {
					String key = "output_" + i;
					String pdb = getString(outputPdbsContent, key);
					if (pdb != null && !pdb.isBlank()) {
						backbones.get(i).setStructure(pdb);
					}
				}
			}
			backboneRepository.saveAll(backbones);
		} else if (STATUS_ERROR.equals(status)) {
			String errorDetails = getString(response, "error_details");
			if (errorDetails == null) {
				errorDetails = getString(response, "error");
			}
			if (errorDetails == null) {
				errorDetails = getString(response, "message");
			}
			if (errorDetails == null) {
				errorDetails = "Unknown error";
			}
			for (Backbone bb : backbones) {
				bb.setError(errorDetails);
			}
			backboneRepository.saveAll(backbones);
		} else {
			// RUNNING or other: just update status
			backboneRepository.saveAll(backbones);
		}

		return backbones;
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
