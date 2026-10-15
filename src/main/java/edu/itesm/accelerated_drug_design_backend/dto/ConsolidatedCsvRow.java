package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * One consolidated-CSV row. Projection only: no pdb, fasta, bestPdb or backbone structure.
 * {@code jobId} and {@code n} drive keyset paging and are not written to the file.
 */
public record ConsolidatedCsvRow(
		Long backboneId,
		String contigs,
		String hotspots,
		String chainsToRemove,
		Integer backboneIterations,
		Double temperature,
		Double ptm,
		Double iPtm,
		String pae,
		String iPae,
		Double plddt,
		Double rmsd,
		String mpnn,
		String seq,
		Long jobId,
		Integer n
) {}
