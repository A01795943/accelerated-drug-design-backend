package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.dto.CreateProjectRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProjectSummaryDto;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {

	private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

	private final ProjectRepository projectRepository;
	private final RestTemplate restTemplate;
	private final PdbCacheService pdbCache;
	private final EcNumberResolver ecNumberResolver;

	public ProjectService(ProjectRepository projectRepository, RestTemplate restTemplate, PdbCacheService pdbCache,
			EcNumberResolver ecNumberResolver) {
		this.projectRepository = projectRepository;
		this.restTemplate = restTemplate;
		this.pdbCache = pdbCache;
		this.ecNumberResolver = ecNumberResolver;
	}

	public List<ProjectSummaryDto> findAll() {
		return projectRepository.findAllSummaries();
	}

	public ProjectSummaryDto findById(Long id) {
		return projectRepository.findSummaryById(id)
				.orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
	}

	/** Entidad completa para uso interno (p. ej. asociar a Backbone/GenerationJob). */
	public Project findEntityById(Long id) {
		return projectRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
	}

	public String getTarget(Long projectId) {
		return pdbCache.getTarget(projectId)
				.orElseGet(() -> {
					String value = projectRepository.findTargetById(projectId)
							.orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
					pdbCache.putTarget(projectId, value);
					return value;
				});
	}

	/**
	 * Recalculates EC metadata from the stored target. Idempotent.
	 * Keeps an existing target PDB ID; fills it from the HEADER only when it is missing.
	 */
	public ProjectSummaryDto resolveEc(Long projectId) {
		Project project = findEntityById(projectId);
		String targetPdbId = project.getTargetPdbId();
		if (targetPdbId == null || targetPdbId.isBlank()) {
			targetPdbId = ecNumberResolver.extractPdbIdFromHeader(project.getTarget()).orElse(null);
		}
		project.setTargetPdbId(targetPdbId);
		ecNumberResolver.resolve(targetPdbId, project.getTarget()).ifPresentOrElse(info -> {
			project.setEcNumber(info.ecNumber());
			project.setEcLineage(info.ecLineage());
		}, () -> {
			project.setEcNumber(null);
			project.setEcLineage(null);
		});
		projectRepository.save(project);
		return findById(projectId);
	}

	public String getComplex(Long projectId) {
		return pdbCache.getComplex(projectId)
				.orElseGet(() -> {
					String value = projectRepository.findComplexById(projectId)
							.orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
					pdbCache.putComplex(projectId, value);
					return value;
				});
	}

	public Project createProject(CreateProjectRequest request) {
		Project project = new Project();
		project.setName(request.getName());
		project.setDescription(request.getDescription());

		String targetContent = resolveTarget(request);
		project.setTarget(targetContent);

		String complexContent = resolveComplex(request);
		project.setComplex(complexContent);

		applyTargetEcMetadata(project, request, targetContent);

		Project saved = projectRepository.save(project);
		pdbCache.putTarget(saved.getId(), targetContent);
		pdbCache.putComplex(saved.getId(), complexContent);
		return saved;
	}

	private void applyTargetEcMetadata(Project project, CreateProjectRequest request, String targetContent) {
		try {
			String targetPdbId = null;
			if (request.isTargetDownloadFromWeb()) {
				String targetName = request.getTargetName();
				if (targetName != null && !targetName.isBlank()) {
					targetPdbId = targetName.strip().toUpperCase(Locale.ROOT);
				}
			} else {
				targetPdbId = ecNumberResolver.extractPdbIdFromHeader(targetContent).orElse(null);
			}
			project.setTargetPdbId(targetPdbId);
			ecNumberResolver.resolve(targetPdbId, targetContent).ifPresent(info -> {
				project.setEcNumber(info.ecNumber());
				project.setEcLineage(info.ecLineage());
			});
		} catch (Throwable t) {
			log.warn("Skipping EC metadata for new project '{}'", request.getName(), t);
			project.setTargetPdbId(null);
			project.setEcNumber(null);
			project.setEcLineage(null);
		}
	}

	private String resolveTarget(CreateProjectRequest request) {
		if (request.isTargetDownloadFromWeb()) {
			if (request.getTargetName() == null || request.getTargetName().isBlank()) {
				throw new IllegalArgumentException("targetName is required when targetDownloadFromWeb is true");
			}
			return fetchPdbFromRcsb(request.getTargetName());
		}
		return request.getTarget();
	}

	private String resolveComplex(CreateProjectRequest request) {
		if (request.isComplexDownloadFromWeb()) {
			if (request.getComplexName() == null || request.getComplexName().isBlank()) {
				throw new IllegalArgumentException("complexName is required when complexDownloadFromWeb is true");
			}
			return fetchPdbFromRcsb(request.getComplexName());
		}
		return request.getComplex();
	}

	private static final String RCSB_PDB_DOWNLOAD_URL = "https://files.rcsb.org/download/%s.pdb";
	static final String RCSB_POLYMER_ENTITY_URL = "https://data.rcsb.org/rest/v1/core/polymer_entity/%s/1";

	private String fetchPdbFromRcsb(String pdbId) {
		String id = pdbId.strip();
		if (id.isEmpty()) {
			throw new IllegalArgumentException("PDB ID cannot be blank");
		}
		// PDB IDs are typically 4 alphanumeric characters; allow only safe characters for URL
		if (!id.matches("[A-Za-z0-9]+")) {
			throw new IllegalArgumentException("Invalid PDB ID (alphanumeric only): " + pdbId);
		}
		String url = RCSB_PDB_DOWNLOAD_URL.formatted(id);
		try {
			return restTemplate.getForObject(url, String.class);
		} catch (RestClientException e) {
			throw new IllegalStateException("Failed to download PDB from " + url + ": " + e.getMessage(), e);
		}
	}
}
