package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * DTO para detalle de job en pantalla: solo id, runId, status, error, totalRecords, backbone.
 * Sin bestPdb, fasta ni outputCsv (se obtienen por endpoints separados).
 */
public record GenerationJobDetailDto(
	Long id,
	String runId,
	String status,
	String error,
	Integer totalRecords,
	Long backboneId,
	String backboneName
) {}
