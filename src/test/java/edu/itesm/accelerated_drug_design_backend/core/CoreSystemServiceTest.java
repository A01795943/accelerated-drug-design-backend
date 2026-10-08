package edu.itesm.accelerated_drug_design_backend.core;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoreSystemServiceTest {

	private RestTemplate restTemplate;
	private MockRestServiceServer server;
	private CoreSystemService service;
	private CoreInstance instance;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.createServer(restTemplate);
		service = new CoreSystemService(1000, 2000, (connect, read) -> restTemplate);
		instance = new CoreInstance();
		instance.setId(1L);
		instance.setBaseUrl("http://127.0.0.1:8000");
	}

	@Test
	void healthCheck_returnsUpWhenHealthResponds() {
		server.expect(requestTo("http://127.0.0.1:8000/health"))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		assertEquals(CoreInstance.HEALTH_UP, service.healthCheck(instance));
		server.verify();
	}

	@Test
	void healthCheck_returnsDownWhenHealthFails() {
		server.expect(requestTo("http://127.0.0.1:8000/health"))
				.andRespond(withServerError());

		assertEquals(CoreInstance.HEALTH_DOWN, service.healthCheck(instance));
		server.verify();
	}

	@Test
	void usesInstanceTimeoutsWhenPresent() {
		instance.setConnectTimeoutMs(50);
		instance.setReadTimeoutMs(80);
		assertEquals(50, service.effectiveConnectTimeout(instance));
		assertEquals(80, service.effectiveReadTimeout(instance));
	}

	@Test
	void fallsBackToAppCoreTimeoutsWhenInstanceOmitsThem() {
		assertEquals(1000, service.effectiveConnectTimeout(instance));
		assertEquals(2000, service.effectiveReadTimeout(instance));
	}

	@Test
	void getSystemMetrics_readsMetricsEndpoint() {
		server.expect(requestTo("http://127.0.0.1:8000/metrics/system"))
				.andRespond(withSuccess("{\"cpu\":{\"percent\":1.0}}", MediaType.APPLICATION_JSON));

		assertEquals(1.0, ((java.util.Map<?, ?>) service.getSystemMetrics(instance).get("cpu")).get("percent"));
		server.verify();
	}
}
