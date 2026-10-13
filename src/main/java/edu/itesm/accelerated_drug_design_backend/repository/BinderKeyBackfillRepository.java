package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecordId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BinderKeyBackfillRepository extends JpaRepository<GenerationJobRecord, GenerationJobRecordId> {

	/**
	 * Keyset page for the one-time binder-key copy. Selects backbone id, seq,
	 * job id and n. Does not select pdb.
	 */
	@Query("""
			SELECT b.id, r.seq, r.generationJobId, r.n
			FROM GenerationJobRecord r
			JOIN r.generationJob j
			JOIN j.backbone b
			WHERE r.seq IS NOT NULL
			  AND (r.generationJobId > :jobId OR (r.generationJobId = :jobId AND r.n > :n))
			ORDER BY r.generationJobId ASC, r.n ASC
			""")
	List<Object[]> findSeqPageForBinderBackfill(
			@Param("jobId") long jobId,
			@Param("n") int n,
			Pageable pageable);
}
