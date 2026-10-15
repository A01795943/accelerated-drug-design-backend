package edu.itesm.accelerated_drug_design_backend;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AcceleratedDrugDesignBackendApplicationTests {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void contextLoads() {
	}

	@Test
	void flywaySeedsCorePrincipalFromPlaceholder() {
		CoreInstance instance = entityManager.createQuery(
						"SELECT c FROM CoreInstance c WHERE c.name = :name", CoreInstance.class)
				.setParameter("name", "core-principal")
				.getSingleResult();
		assertEquals("http://localhost:8000", instance.getBaseUrl());
		assertTrue(instance.isEnabled());
		assertFalse(instance.isRestrictSchedule());
		assertEquals(CoreInstance.HEALTH_UNKNOWN, instance.getHealthState());
	}

}
