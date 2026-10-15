package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.ConsolidatedCsvRow;
import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobRecordListDto;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecordId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRecordRepository extends JpaRepository<GenerationJobRecord, GenerationJobRecordId> {

	List<GenerationJobRecord> findByGenerationJob_IdOrderByNAsc(Long generationJobId);

	/** Listado sin columna pdb para pantalla de detalle (paginado). */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobRecordListDto(
			r.generationJobId, r.n, r.seq, r.mpnn, r.plddt, r.ptm, r.iPtm, r.pae, r.iPae, r.rmsd
		) FROM GenerationJobRecord r WHERE r.generationJobId = :jobId ORDER BY r.n ASC
		""")
	Page<GenerationJobRecordListDto> findRecordsListByJobId(@Param("jobId") Long jobId, Pageable pageable);

	/** Listado sin pdb para construir CSV (todos los registros). */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.GenerationJobRecordListDto(
			r.generationJobId, r.n, r.seq, r.mpnn, r.plddt, r.ptm, r.iPtm, r.pae, r.iPae, r.rmsd
		) FROM GenerationJobRecord r WHERE r.generationJobId = :jobId ORDER BY r.n ASC
		""")
	List<GenerationJobRecordListDto> findRecordsListByJobIdAll(@Param("jobId") Long jobId);

	/**
	 * Keyset page of consolidated CSV rows. Selects scalar columns only
	 * (no record pdb, job fasta, job bestPdb, job outputCsv, or backbone structure).
	 */
	@Query("""
		SELECT new edu.itesm.accelerated_drug_design_backend.dto.ConsolidatedCsvRow(
			b.id, b.contigs, b.hotspots, b.chainsToRemove, b.iterations, j.temperature,
			r.ptm, r.iPtm, r.pae, r.iPae, r.plddt, r.rmsd, r.mpnn, r.seq, j.id, r.n
		)
		FROM GenerationJobRecord r
		JOIN r.generationJob j
		JOIN j.backbone b
		WHERE j.project.id = :projectId
		  AND j.status = :status
		  AND j.id IN :jobIds
		  AND (j.id > :lastJobId OR (j.id = :lastJobId AND r.n > :lastN))
		ORDER BY j.id ASC, r.n ASC
		""")
	List<ConsolidatedCsvRow> findConsolidatedBatch(
			@Param("projectId") Long projectId,
			@Param("status") String status,
			@Param("jobIds") Collection<Long> jobIds,
			@Param("lastJobId") long lastJobId,
			@Param("lastN") int lastN,
			Pageable pageable);

	/** Solo columna pdb del registro (n) del job, verificando que el job pertenezca al proyecto. */
	@Query("SELECT r.pdb FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId AND r.n = :n")
	Optional<String> findPdbByProjectIdJobIdAndN(@Param("projectId") Long projectId, @Param("jobId") Long jobId, @Param("n") Integer n);

	Page<GenerationJobRecord> findByGenerationJob_IdOrderByNAsc(Long generationJobId, Pageable pageable);

	long countByGenerationJob_Id(Long generationJobId);

	void deleteByGenerationJob_Id(Long generationJobId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			DELETE FROM GenerationJobRecord r
			WHERE r.generationJobId = :jobId AND r.n >= :fromN AND r.n < :toN
			""")
	int deleteByJobIdAndNRange(@Param("jobId") Long jobId, @Param("fromN") int fromN, @Param("toN") int toN);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM GenerationJobRecord r WHERE r.minibatchId = :minibatchId")
	int deleteByMinibatchId(@Param("minibatchId") Long minibatchId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM GenerationJobRecord r WHERE r.generationJobId = :jobId AND r.n IN :ns")
	int deleteByJobIdAndNs(@Param("jobId") Long jobId, @Param("ns") Collection<Integer> ns);

	/** Solo columna plddt, para EDA. Job debe pertenecer al proyecto. */
	@Query("SELECT r.plddt FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<Double> findPlddtByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.ptm FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<Double> findPtmByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.iPtm FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<Double> findIPtmByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.rmsd FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<Double> findRmsdByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.mpnn FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<String> findMpnnByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.pae FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<String> findPaeByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT r.iPae FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId ORDER BY r.n ASC")
	List<String> findIPaeByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT max(r.ptm) FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId")
	Double findMaxPtmByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	@Query("SELECT max(r.iPtm) FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId")
	Double findMaxIPtmByProjectIdAndJobId(@Param("projectId") Long projectId, @Param("jobId") Long jobId);

	/**
	 * Sequences already stored for this backbone, except the rows this minibatch owns.
	 * A retry of the same minibatch must still be allowed to recompute those rows.
	 * Does not select pdb.
	 */
	@Query("""
		SELECT r.seq FROM GenerationJobRecord r
		JOIN r.generationJob j
		WHERE j.backbone.id = :backboneId
		  AND r.seq IS NOT NULL
		  AND NOT (j.id = :jobId AND r.n >= :fromN AND r.n < :toN)
		""")
	List<String> findSeqsByBackboneExcludingRange(
			@Param("backboneId") Long backboneId,
			@Param("jobId") Long jobId,
			@Param("fromN") int fromN,
			@Param("toN") int toN);
}
