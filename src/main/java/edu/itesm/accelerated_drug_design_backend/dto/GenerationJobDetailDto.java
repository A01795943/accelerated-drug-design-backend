package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * DTO para detalle de job en pantalla: solo id, runId, status, error, totalRecords, backbone.
 * Sin bestPdb, fasta ni outputCsv (se obtienen por endpoints separados).
 * {@code coreInstanceName} is resolved from {@code core_instance_id} (null while QUEUED).
 */
public record GenerationJobDetailDto(
	Long id,
	String runId,
	String status,
	String error,
	Integer totalRecords,
	Long backboneId,
	String backboneName,
	Long coreInstanceId,
	String coreInstanceName,
	Integer completedMinibatches,
	Integer totalMinibatches
) {}
