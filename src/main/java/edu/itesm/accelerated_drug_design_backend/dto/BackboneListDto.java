package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * DTO para listado de backbones: id, nombre, runID, status, error, contigs, hotspots, chainsToRemove, iterations.
 * Sin structure (PDB); se obtiene con GET .../backbones/{backboneId}/structure.
 */
public record BackboneListDto(
	Long id,
	String name,
	String runID,
	String status,
	String error,
	String contigs,
	String hotspots,
	String chainsToRemove,
	Integer iterations
) {}
