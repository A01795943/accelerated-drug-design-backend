package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackboneRepository extends JpaRepository<Backbone, Long> {

	List<Backbone> findByProject_IdOrderByIdAsc(Long projectId);

	List<Backbone> findByProject_IdAndRunIDOrderByIdAsc(Long projectId, String runID);

	java.util.Optional<Backbone> findByProject_IdAndId(Long projectId, Long id);
}
