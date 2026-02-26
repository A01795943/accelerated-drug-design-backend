package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	/** Solo id, nombre y descripción para el listado. */
	@Query("SELECT new edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto(p.id, p.name, p.description) FROM Project p ORDER BY p.id")
	List<ProjectSummaryDto> findAllSummaries();

	/** Solo id, nombre y descripción para detalle (sin target/complex). */
	@Query("SELECT new edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto(p.id, p.name, p.description) FROM Project p WHERE p.id = :id")
	Optional<ProjectSummaryDto> findSummaryById(@Param("id") Long id);

	/** Solo columna target para no cargar el resto. */
	@Query("SELECT p.target FROM Project p WHERE p.id = :id")
	Optional<String> findTargetById(@Param("id") Long id);

	/** Solo columna complex para no cargar el resto. */
	@Query("SELECT p.complex FROM Project p WHERE p.id = :id")
	Optional<String> findComplexById(@Param("id") Long id);
}
