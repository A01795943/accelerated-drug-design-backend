package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves core instances for runtime calls. {@code app.core.base-url} is only a
 * bootstrap fallback when id=1 is missing (e.g. re-seeded DB).
 */
@Component
public class CoreInstanceResolver {

	private final CoreInstanceRepository coreInstanceRepository;
	private final String bootstrapBaseUrl;

	public CoreInstanceResolver(
			CoreInstanceRepository coreInstanceRepository,
			@Value("${app.core.base-url:}") String bootstrapBaseUrl) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.bootstrapBaseUrl = bootstrapBaseUrl != null ? bootstrapBaseUrl.replaceAll("/$", "") : "";
	}

	public CoreInstance requireDefault() {
		return coreInstanceRepository.findById(1L)
				.or(() -> bootstrapBaseUrl.isBlank()
						? Optional.empty()
						: coreInstanceRepository.findByBaseUrl(bootstrapBaseUrl))
				.or(coreInstanceRepository::findTopByOrderByIdAsc)
				.orElseThrow(() -> new IllegalStateException("No core instance is configured"));
	}

	public Optional<CoreInstance> findById(Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return coreInstanceRepository.findById(id);
	}
}
