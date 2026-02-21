package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CreateProjectRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final RestTemplate restTemplate;

	public ProjectService(ProjectRepository projectRepository, RestTemplate restTemplate) {
		this.projectRepository = projectRepository;
		this.restTemplate = restTemplate;
	}

	public List<Project> findAll() {
		return projectRepository.findAll();
	}

	public Project findById(Long id) {
		return projectRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
	}

	public Project createProject(CreateProjectRequest request) {
		Project project = new Project();
		project.setName(request.getName());
		project.setDescription(request.getDescription());

		String targetContent = resolveTarget(request);
		project.setTarget(targetContent);

		String complexContent = resolveComplex(request);
		project.setComplex(complexContent);

		return projectRepository.save(project);
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
