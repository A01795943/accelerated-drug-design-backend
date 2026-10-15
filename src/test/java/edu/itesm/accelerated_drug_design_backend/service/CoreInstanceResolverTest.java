package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreInstanceResolverTest {

	@Mock
	private CoreInstanceRepository repository;

	private CoreInstanceResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new CoreInstanceResolver(repository, "http://localhost:8000");
	}

	@Test
	void requireDefault_prefersIdOne() {
		CoreInstance one = instance(1L, "http://localhost:8000");
		when(repository.findById(1L)).thenReturn(Optional.of(one));

		assertEquals(1L, resolver.requireDefault().getId());
	}

	@Test
	void requireDefault_fallsBackToBootstrapBaseUrl() {
		CoreInstance seeded = instance(4L, "http://localhost:8000");
		when(repository.findById(1L)).thenReturn(Optional.empty());
		when(repository.findByBaseUrl("http://localhost:8000")).thenReturn(Optional.of(seeded));

		assertEquals(4L, resolver.requireDefault().getId());
	}

	@Test
	void requireDefault_throwsWhenNoneConfigured() {
		when(repository.findById(1L)).thenReturn(Optional.empty());
		when(repository.findByBaseUrl("http://localhost:8000")).thenReturn(Optional.empty());
		when(repository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class, resolver::requireDefault);
	}

	private static CoreInstance instance(Long id, String baseUrl) {
		CoreInstance instance = new CoreInstance();
		instance.setId(id);
		instance.setBaseUrl(baseUrl);
		return instance;
	}
}
