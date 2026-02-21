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
	 * Get MPNN run status. Returns null if the request fails.
	 * When batch is null: returns status and when COMPLETED also summary (fasta, best_pdb, total_records, total_batches).
	 * When batch is not null: returns that batch of detail records (run_id, n, pdb, seq, metrics).
	 *
	 * @param runId run identifier
	 * @param batch 0-based batch index, or null for summary / first call
	 */
	Map<String, Object> getMpnnStatus(String runId, Integer batch);
}
