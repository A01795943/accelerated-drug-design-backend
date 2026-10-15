package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;

import java.util.Map;

/**
 * Interface for all HTTP communication with an external core instance
 * (rfdiffusion, mpnn, health, etc.). Implementations perform the actual requests.
 */
public interface CoreSystemInterface {

	/**
	 * Trigger an rfdiffusion run on the given instance. On failure throws so the caller can set backbones to ERROR.
	 */
	void triggerRfdiffusion(CoreInstance instance, RfdiffusionRunRequest request);

	/**
	 * Get rfdiffusion run status from the given instance. Returns null if the request fails.
	 * Response map typically contains: status, output_pdbs_content, error_details, etc.
	 */
	Map<String, Object> getRfdiffusionStatus(CoreInstance instance, String runId);

	/**
	 * Trigger an MPNN run on the given instance. On failure throws so the caller can set the job to ERROR.
	 */
	void triggerMpnn(CoreInstance instance, ProteinMpnnRunRequest request);

	/**
	 * Get MPNN run status (overall only) from the given instance. Returns null if the request fails.
	 * When COMPLETED, response includes summary (fasta_content, best_pdb_content) and pagination (total_records, total_batches).
	 * Detail records are fetched separately via {@link #getMpnnStatusDetail(CoreInstance, String, int)}.
	 */
	Map<String, Object> getMpnnStatus(CoreInstance instance, String runId);

	/**
	 * GET /run/mpnn/status/{runId} without mapping 404 to an error body.
	 * {@link RunPresence#PRESENT} when the core knows the run, {@link RunPresence#ABSENT} on 404,
	 * {@link RunPresence#UNKNOWN} when the instance cannot be reached.
	 */
	RunPresence probeMpnnRun(CoreInstance instance, String runId);

	/**
	 * Get one batch of MPNN detail records for a completed run from the given instance. Returns null if the request fails.
	 */
	Map<String, Object> getMpnnStatusDetail(CoreInstance instance, String runId, int batch);

	/**
	 * GET {baseUrl}/health. Returns {@link CoreInstance#HEALTH_UP} if the instance responds
	 * within the connect timeout, otherwise {@link CoreInstance#HEALTH_DOWN}.
	 * Does not persist health_state.
	 */
	String healthCheck(CoreInstance instance);

	/**
	 * GET {baseUrl}/metrics/system. Returns null if the request fails.
	 */
	Map<String, Object> getSystemMetrics(CoreInstance instance);

	/**
	 * Same as {@link #getSystemMetrics(CoreInstance)} with explicit timeouts (metrics poller uses 5s).
	 */
	Map<String, Object> getSystemMetrics(CoreInstance instance, int connectTimeoutMs, int readTimeoutMs);
}
