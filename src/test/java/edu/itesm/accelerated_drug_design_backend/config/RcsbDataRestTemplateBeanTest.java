package edu.itesm.accelerated_drug_design_backend.config;

import edu.itesm.accelerated_drug_design_backend.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
@ActiveProfiles("test")
class RcsbDataRestTemplateBeanTest {

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	@Qualifier("rcsbDataRestTemplate")
	private RestTemplate rcsbDataRestTemplate;

	@Autowired
	private ProjectService projectService;

	@Test
	void dataApiClientDoesNotReplaceFilesClient() throws Exception {
		assertNotSame(restTemplate, rcsbDataRestTemplate);

		Field field = ProjectService.class.getDeclaredField("restTemplate");
		field.setAccessible(true);
		assertSame(restTemplate, field.get(projectService));

		assertEquals(10_000, timeout(restTemplate, "connectTimeout"));
		assertEquals(120_000, timeout(restTemplate, "readTimeout"));
		assertEquals(2_000, timeout(rcsbDataRestTemplate, "connectTimeout"));
		assertEquals(4_000, timeout(rcsbDataRestTemplate, "readTimeout"));
	}

	private static int timeout(RestTemplate template, String fieldName) throws Exception {
		SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) template.getRequestFactory();
		Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(factory);
	}
}
