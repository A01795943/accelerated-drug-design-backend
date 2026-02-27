package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.GenerationJobRecordListDto;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecordId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/** Solo columna pdb del registro (n) del job, verificando que el job pertenezca al proyecto. */
	@Query("SELECT r.pdb FROM GenerationJobRecord r JOIN r.generationJob j WHERE j.id = :jobId AND j.project.id = :projectId AND r.n = :n")
	Optional<String> findPdbByProjectIdJobIdAndN(@Param("projectId") Long projectId, @Param("jobId") Long jobId, @Param("n") Integer n);

	Page<GenerationJobRecord> findByGenerationJob_IdOrderByNAsc(Long generationJobId, Pageable pageable);

	long countByGenerationJob_Id(Long generationJobId);

	void deleteByGenerationJob_Id(Long generationJobId);

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
}
