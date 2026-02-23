package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of {@link CoreSystemInterface} using RestTemplate.
 * All HTTP requests to the core system (rfdiffusion, mpnn) go through this class.
 * Base URL is configured via app.core.base-url (per profile: local or docker).
 */
@Service
public class CoreSystemService implements CoreSystemInterface {

	private static final Logger log = LoggerFactory.getLogger(CoreSystemService.class);

	private static final String RFDIFFUSION_PATH = "/run/rfdiffusion";
	private static final String MPNN_PATH = "/run/mpnn";

	private final RestTemplate restTemplate;
	private final String coreBaseUrl;

	public CoreSystemService(RestTemplate restTemplate,
			@Value("${app.core.base-url}") String coreBaseUrl) {
		this.restTemplate = restTemplate;
		this.coreBaseUrl = coreBaseUrl != null ? coreBaseUrl.replaceAll("/$", "") : "";
	}

	private String getRfdiffusionBase() {
		return coreBaseUrl + RFDIFFUSION_PATH;
	}

	private String getMpnnBase() {
		return coreBaseUrl + MPNN_PATH;
	}

	@Override
	public void triggerRfdiffusion(RfdiffusionRunRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<RfdiffusionRunRequest> entity = new HttpEntity<>(request, headers);
		restTemplate.postForEntity(getRfdiffusionBase(), entity, String.class);
		log.info("Rfdiffusion run triggered: runId={}", request.getRunId());
	}

	@Override
	public Map<String, Object> getRfdiffusionStatus(String runId) {
		String statusUrl = getRfdiffusionBase() + "/status/" + runId;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restTemplate.getForObject(statusUrl, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get status from {}: {}", statusUrl, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void triggerMpnn(ProteinMpnnRunRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<ProteinMpnnRunRequest> entity = new HttpEntity<>(request, headers);
		restTemplate.postForEntity(getMpnnBase(), entity, String.class);
		log.info("ProteinMPNN run triggered: runId={}", request.getRunId());
	}

	@Override
	public Map<String, Object> getMpnnStatus(String runId) {
		String url = getMpnnBase() + "/status/" + runId;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get MPNN status from {}: {}", url, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public Map<String, Object> getMpnnStatusDetail(String runId, int batch) {
		String url = getMpnnBase() + "/status/" + runId + "/detail?batch=" + batch;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get MPNN status detail from {}: {}", url, e.getMessage(), e);
			return null;
		}
	}
}
