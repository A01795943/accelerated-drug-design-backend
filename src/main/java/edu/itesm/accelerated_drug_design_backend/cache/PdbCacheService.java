package edu.itesm.accelerated_drug_design_backend.cache;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for PDB content: project target, project complex,
 * backbone structure, generation job best PDB, and generation job record PDBs.
 * Read-through: services check cache first, then load from DB and store in cache.
 */
@Service
public class PdbCacheService {

	private static final String PREFIX_TARGET = "target:";
	private static final String PREFIX_COMPLEX = "complex:";
	private static final String PREFIX_BACKBONE = "backbone:";
	private static final String PREFIX_BEST_PDB = "bestPdb:";
	private static final String PREFIX_RECORD_PDB = "recordPdb:";

	private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

	// --- Target (project) ---

	public Optional<String> getTarget(Long projectId) {
		return Optional.ofNullable(cache.get(PREFIX_TARGET + projectId));
	}

	public void putTarget(Long projectId, String value) {
		if (value != null) {
			cache.put(PREFIX_TARGET + projectId, value);
		}
	}

	public void evictTarget(Long projectId) {
		cache.remove(PREFIX_TARGET + projectId);
	}

	// --- Complex (project) ---

	public Optional<String> getComplex(Long projectId) {
		return Optional.ofNullable(cache.get(PREFIX_COMPLEX + projectId));
	}

	public void putComplex(Long projectId, String value) {
		if (value != null) {
			cache.put(PREFIX_COMPLEX + projectId, value);
		}
	}

	public void evictComplex(Long projectId) {
		cache.remove(PREFIX_COMPLEX + projectId);
	}

	// --- Backbone structure ---

	public Optional<String> getBackboneStructure(Long projectId, Long backboneId) {
		return Optional.ofNullable(cache.get(PREFIX_BACKBONE + projectId + ":" + backboneId));
	}

	public void putBackboneStructure(Long projectId, Long backboneId, String value) {
		if (value != null) {
			cache.put(PREFIX_BACKBONE + projectId + ":" + backboneId, value);
		}
	}

	public void evictBackboneStructure(Long projectId, Long backboneId) {
		cache.remove(PREFIX_BACKBONE + projectId + ":" + backboneId);
	}

	// --- Best PDB (generation job) ---

	public Optional<String> getBestPdb(Long projectId, Long jobId) {
		return Optional.ofNullable(cache.get(PREFIX_BEST_PDB + projectId + ":" + jobId));
	}

	public void putBestPdb(Long projectId, Long jobId, String value) {
		if (value != null) {
			cache.put(PREFIX_BEST_PDB + projectId + ":" + jobId, value);
		}
	}

	public void evictBestPdb(Long projectId, Long jobId) {
		cache.remove(PREFIX_BEST_PDB + projectId + ":" + jobId);
	}

	// --- Record PDB (generation job record) ---

	public Optional<String> getRecordPdb(Long projectId, Long jobId, Integer n) {
		return Optional.ofNullable(cache.get(keyRecordPdb(projectId, jobId, n)));
	}

	public void putRecordPdb(Long projectId, Long jobId, Integer n, String value) {
		if (value != null) {
			cache.put(keyRecordPdb(projectId, jobId, n), value);
		}
	}

	private static String keyRecordPdb(Long projectId, Long jobId, Integer n) {
		return PREFIX_RECORD_PDB + projectId + ":" + jobId + ":" + n;
	}

	/** Evicts all cached record PDBs for the given job (e.g. after job completion updates). */
	public void evictRecordPdbsForJob(Long projectId, Long jobId) {
		String prefix = PREFIX_RECORD_PDB + projectId + ":" + jobId + ":";
		cache.keySet().removeIf(key -> key.startsWith(prefix));
	}
}
