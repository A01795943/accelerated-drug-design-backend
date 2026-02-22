package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;

import java.util.Map;

/**
 * Interface for all HTTP communication with the external core system
 * (rfdiffusion, mpnn, etc.). Implementations perform the actual requests.
 */
public interface CoreSystemInterface {

	/**
	 * Trigger an rfdiffusion run. On failure throws so the caller can set backbones to ERROR.
	 */
	void triggerRfdiffusion(RfdiffusionRunRequest request);

	/**
	 * Get rfdiffusion run status. Returns null if the request fails.
	 * Response map typically contains: status, output_pdbs_content, error_details, etc.
	 */
	Map<String, Object> getRfdiffusionStatus(String runId);

	/**
	 * Trigger an MPNN run. On failure throws so the caller can set the job to ERROR.
	 */
	void triggerMpnn(ProteinMpnnRunRequest request);

	/**
	 * Get MPNN run status (overall only). Returns null if the request fails.
	 * When COMPLETED, response includes summary (fasta_content, best_pdb_content) and pagination (total_records, total_batches).
	 * Detail records are fetched separately via {@link #getMpnnStatusDetail(String, int)}.
	 *
	 * @param runId run identifier
	 */
	Map<String, Object> getMpnnStatus(String runId);

	/**
	 * Get one batch of MPNN detail records for a completed run. Returns null if the request fails.
	 * Response contains "detail" array and "pagination" (total_records, total_batches, batch_size).
	 *
	 * @param runId run identifier
	 * @param batch 0-based batch index
	 */
	Map<String, Object> getMpnnStatusDetail(String runId, int batch);
}
