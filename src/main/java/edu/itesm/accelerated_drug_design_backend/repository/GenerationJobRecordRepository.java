package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecordId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenerationJobRecordRepository extends JpaRepository<GenerationJobRecord, GenerationJobRecordId> {

	List<GenerationJobRecord> findByGenerationJob_IdOrderByNAsc(Long generationJobId);

	Page<GenerationJobRecord> findByGenerationJob_IdOrderByNAsc(Long generationJobId, Pageable pageable);

	long countByGenerationJob_Id(Long generationJobId);

	void deleteByGenerationJob_Id(Long generationJobId);
}
