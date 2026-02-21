package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
public class CoreSystemService implements CoreSystemInterface {

	private static final Logger log = LoggerFactory.getLogger(CoreSystemService.class);

	private static final String CORE_SYSTEM_IP = "34.42.186.254";
	private static final int CORE_SYSTEM_PORT = 8000;
	private static final String RFDIFFUSION_BASE = "http://" + CORE_SYSTEM_IP + ":" + CORE_SYSTEM_PORT + "/run/rfdiffusion";
	private static final String MPNN_BASE = "http://" + CORE_SYSTEM_IP + ":" + CORE_SYSTEM_PORT + "/run/mpnn";

	private final RestTemplate restTemplate;

	public CoreSystemService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	@Override
	public void triggerRfdiffusion(RfdiffusionRunRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<RfdiffusionRunRequest> entity = new HttpEntity<>(request, headers);
		restTemplate.postForEntity(RFDIFFUSION_BASE, entity, String.class);
		log.info("Rfdiffusion run triggered: runId={}", request.getRunId());
	}

	@Override
	public Map<String, Object> getRfdiffusionStatus(String runId) {
		String statusUrl = RFDIFFUSION_BASE + "/status/" + runId;
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
		restTemplate.postForEntity(MPNN_BASE, entity, String.class);
		log.info("ProteinMPNN run triggered: runId={}", request.getRunId());
	}

	@Override
	public Map<String, Object> getMpnnStatus(String runId, Integer batch) {
		String url = MPNN_BASE + "/status/" + runId;
		if (batch != null) {
			url = url + "?batch=" + batch;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restTemplate.getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get MPNN status from {}: {}", url, e.getMessage(), e);
			return null;
		}
	}
}
