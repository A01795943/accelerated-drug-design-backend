package edu.itesm.accelerated_drug_design_backend.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoreInstanceControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CoreInstanceRepository coreInstanceRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private BackboneRepository backboneRepository;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void adminCanCreatePatchAndDeleteInstanceWithWindows() throws Exception {
		mockMvc.perform(post("/api/core-instances")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "core-lab",
								  "baseUrl": "http://192.168.1.10:8000",
								  "enabled": true,
								  "restrictSchedule": true,
								  "maxJobMinutes": 90,
								  "windows": [
								    {"dayOfWeek": 1, "startTime": "08:00", "endTime": "20:00"},
								    {"dayOfWeek": 5, "startTime": "20:00", "endTime": "08:00"}
								  ]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("core-lab"))
				.andExpect(jsonPath("$.baseUrl").value("http://192.168.1.10:8000"))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.restrictSchedule").value(true))
				.andExpect(jsonPath("$.maxJobMinutes").value(90))
				.andExpect(jsonPath("$.healthState").value("UNKNOWN"))
				.andExpect(jsonPath("$.lastHealthAt").value(nullValue()))
				.andExpect(jsonPath("$.currentRunId").value(nullValue()))
				.andExpect(jsonPath("$.windows", hasSize(2)))
				.andExpect(jsonPath("$.windows[0].dayOfWeek").value(1))
				.andExpect(jsonPath("$.windows[0].startTime").value("08:00"))
				.andExpect(jsonPath("$.windows[1].startTime").value("20:00"))
				.andExpect(jsonPath("$.windows[1].endTime").value("08:00"));

		long id = coreInstanceRepository.findAll().stream()
				.filter(i -> "core-lab".equals(i.getName()))
				.findFirst()
				.orElseThrow()
				.getId();

		mockMvc.perform(patch("/api/core-instances/{id}", id)
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "restrictSchedule": false,
								  "windows": [
								    {"dayOfWeek": 7, "startTime": "00:00", "endTime": "23:59"}
								  ]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.restrictSchedule").value(false))
				.andExpect(jsonPath("$.windows", hasSize(1)))
				.andExpect(jsonPath("$.windows[0].dayOfWeek").value(7));

		mockMvc.perform(get("/api/core-instances/{id}", id)
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("core-lab"));

		mockMvc.perform(delete("/api/core-instances/{id}", id)
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isNoContent());
	}

	@Test
	void nonAdminReceives403() throws Exception {
		mockMvc.perform(get("/api/core-instances")
						.header("Authorization", "Bearer " + userToken()))
				.andExpect(status().isForbidden());
	}

	@Test
	void publicBaseUrlIsRejectedWith400() throws Exception {
		mockMvc.perform(post("/api/core-instances")
						.header("Authorization", "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "public-core",
								  "baseUrl": "http://8.8.8.8:8000"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("allowed private CIDR")));
	}

	@Test
	void deleteOccupiedInstanceReturns409() throws Exception {
		CoreInstance instance = new CoreInstance();
		instance.setName("busy-core");
		instance.setBaseUrl("http://10.0.0.9:8000");
		instance.setCurrentRunId("run-busy");
		instance = coreInstanceRepository.save(instance);

		mockMvc.perform(delete("/api/core-instances/{id}", instance.getId())
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", containsString("Disable it")));
	}

	@Test
	void deleteInstanceWithHistoricalRunsReturns409() throws Exception {
		CoreInstance instance = new CoreInstance();
		instance.setName("historic-core");
		instance.setBaseUrl("http://10.0.0.11:8000");
		instance = coreInstanceRepository.save(instance);

		Project project = new Project();
		project.setName("core-history-project");
		project = projectRepository.save(project);

		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus("COMPLETED");
		backbone.setCoreInstanceId(instance.getId());
		backboneRepository.save(backbone);

		mockMvc.perform(delete("/api/core-instances/{id}", instance.getId())
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", containsString("still reference")));
	}

	@Test
	void listIncludesSeededPrincipal() throws Exception {
		mockMvc.perform(get("/api/core-instances")
						.header("Authorization", "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name", is("core-principal")))
				.andExpect(jsonPath("$[0].baseUrl", is("http://localhost:8000")))
				.andExpect(jsonPath("$[0].healthState", is("UNKNOWN")))
				.andExpect(jsonPath("$[0].lastHealthAt").value(nullValue()))
				.andExpect(jsonPath("$[0].currentRunId").value(nullValue()));
	}

	private String adminToken() {
		return token("ROLE_ADMIN");
	}

	private String userToken() {
		return token("ROLE_USER");
	}

	private String token(String role) {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		Algorithm algorithm = Algorithm.HMAC512(secretBytes);
		return JWT.create()
				.withSubject("core-instance-test")
				.withClaim("roles", role)
				.sign(algorithm);
	}
}
