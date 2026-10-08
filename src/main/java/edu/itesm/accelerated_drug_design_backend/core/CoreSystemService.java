package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for a concrete core instance. URLs and timeouts come from the
 * {@link CoreInstance} argument (falling back to app.core.* timeouts), not from
 * the bootstrap singleton app.core.base-url.
 */
@Service
public class CoreSystemService implements CoreSystemInterface {

	private static final Logger log = LoggerFactory.getLogger(CoreSystemService.class);

	private static final String RFDIFFUSION_PATH = "/run/rfdiffusion";
	private static final String MPNN_PATH = "/run/mpnn";
	private static final String HEALTH_PATH = "/health";
	private static final String METRICS_PATH = "/metrics/system";

	@FunctionalInterface
	interface ClientFactory {
		RestTemplate create(int connectTimeoutMs, int readTimeoutMs);
	}

	private final int defaultConnectTimeoutMs;
	private final int defaultReadTimeoutMs;
	private final ClientFactory clientFactory;

	@Autowired
	public CoreSystemService(
			@Value("${app.core.connect-timeout-ms:10000}") int defaultConnectTimeoutMs,
			@Value("${app.core.read-timeout-ms:120000}") int defaultReadTimeoutMs) {
		this(defaultConnectTimeoutMs, defaultReadTimeoutMs, CoreSystemService::buildRestTemplate);
	}

	CoreSystemService(int defaultConnectTimeoutMs, int defaultReadTimeoutMs, ClientFactory clientFactory) {
		this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
		this.defaultReadTimeoutMs = defaultReadTimeoutMs;
		this.clientFactory = clientFactory;
	}

	@Override
	public void triggerRfdiffusion(CoreInstance instance, RfdiffusionRunRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<RfdiffusionRunRequest> entity = new HttpEntity<>(request, headers);
		client(instance).postForEntity(rfdiffusionBase(instance), entity, String.class);
		log.info("Rfdiffusion run triggered: runId={} instanceId={}", request.getRunId(), instance.getId());
	}

	@Override
	public Map<String, Object> getRfdiffusionStatus(CoreInstance instance, String runId) {
		String statusUrl = rfdiffusionBase(instance) + "/status/" + runId;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = client(instance).getForObject(statusUrl, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get status from {}: {}", statusUrl, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void triggerMpnn(CoreInstance instance, ProteinMpnnRunRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<ProteinMpnnRunRequest> entity = new HttpEntity<>(request, headers);
		client(instance).postForEntity(mpnnBase(instance), entity, String.class);
		log.info("ProteinMPNN run triggered: runId={} instanceId={}", request.getRunId(), instance.getId());
	}

	@Override
	public Map<String, Object> getMpnnStatus(CoreInstance instance, String runId) {
		String url = mpnnBase(instance) + "/status/" + runId;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = client(instance).getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get MPNN status from {}: {}", url, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public Map<String, Object> getMpnnStatusDetail(CoreInstance instance, String runId, int batch) {
		String url = mpnnBase(instance) + "/status/" + runId + "/detail?batch=" + batch;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = client(instance).getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get MPNN status detail from {}: {}", url, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public String healthCheck(CoreInstance instance) {
		int connectMs = effectiveConnectTimeout(instance);
		String url = instanceBase(instance) + HEALTH_PATH;
		try {
			clientFactory.create(connectMs, connectMs).getForObject(url, Map.class);
			return CoreInstance.HEALTH_UP;
		} catch (Exception e) {
			log.warn("Health check failed for {}: {}", url, e.getMessage());
			return CoreInstance.HEALTH_DOWN;
		}
	}

	@Override
	public Map<String, Object> getSystemMetrics(CoreInstance instance) {
		return getSystemMetrics(instance, effectiveConnectTimeout(instance), effectiveReadTimeout(instance));
	}

	@Override
	public Map<String, Object> getSystemMetrics(CoreInstance instance, int connectTimeoutMs, int readTimeoutMs) {
		String url = instanceBase(instance) + METRICS_PATH;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = clientFactory.create(connectTimeoutMs, readTimeoutMs).getForObject(url, Map.class);
			return body != null ? body : new LinkedHashMap<>();
		} catch (Exception e) {
			log.warn("Failed to get system metrics from {}: {}", url, e.getMessage());
			return null;
		}
	}

	private RestTemplate client(CoreInstance instance) {
		return clientFactory.create(effectiveConnectTimeout(instance), effectiveReadTimeout(instance));
	}

	int effectiveConnectTimeout(CoreInstance instance) {
		if (instance != null && instance.getConnectTimeoutMs() != null && instance.getConnectTimeoutMs() > 0) {
			return instance.getConnectTimeoutMs();
		}
		return defaultConnectTimeoutMs;
	}

	int effectiveReadTimeout(CoreInstance instance) {
		if (instance != null && instance.getReadTimeoutMs() != null && instance.getReadTimeoutMs() > 0) {
			return instance.getReadTimeoutMs();
		}
		return defaultReadTimeoutMs;
	}

	private String rfdiffusionBase(CoreInstance instance) {
		return instanceBase(instance) + RFDIFFUSION_PATH;
	}

	private String mpnnBase(CoreInstance instance) {
		return instanceBase(instance) + MPNN_PATH;
	}

	private static String instanceBase(CoreInstance instance) {
		if (instance == null || instance.getBaseUrl() == null || instance.getBaseUrl().isBlank()) {
			throw new IllegalArgumentException("Core instance baseUrl is required");
		}
		return instance.getBaseUrl().replaceAll("/$", "");
	}

	private static RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(connectTimeoutMs);
		factory.setReadTimeout(readTimeoutMs);
		return new RestTemplate(factory);
	}
}
