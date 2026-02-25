package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * DTO para listado de registros en detalle de job: métricas y seq, sin columna pdb.
 * El PDB de cada registro se obtiene con GET .../records/{n}/pdb.
 */
public record GenerationJobRecordListDto(
	Long generationJobId,
	Integer n,
	String seq,
	String mpnn,
	Double plddt,
	Double ptm,
	Double iPtm,
	String pae,
	String iPae,
	Double rmsd
) {}
