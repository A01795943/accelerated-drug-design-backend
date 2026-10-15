package edu.itesm.accelerated_drug_design_backend.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectEcMetadataIntegrationTest {

	private static final String ENZYME_API = """
			{"rcsb_polymer_entity":{"pdbx_ec":"3.4.21.4","rcsb_ec_lineage":[{"depth":4,"id":"3.4.21.4","name":"trypsin"}]}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RestTemplate filesRestTemplate;

	@Autowired
	private ProjectRepository projectRepository;

	@MockitoBean(name = "rcsbDataRestTemplate")
	private RestTemplate rcsbDataRestTemplate;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void downloadByPdbIdPersistsUppercaseTargetPdbId() throws Exception {
		MockRestServiceServer files = MockRestServiceServer.createServer(filesRestTemplate);
		files.expect(requestTo("https://files.rcsb.org/download/4Z18.pdb"))
				.andRespond(withSuccess(pdb("4Z18", "COMPND   2 MOLECULE: PD-L1;"), MediaType.TEXT_PLAIN));
		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/4Z18/1", String.class))
				.thenReturn("{\"rcsb_polymer_entity\":{}}");

		mockMvc.perform(post("/api/projects")
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"pdl1-download","targetDownloadFromWeb":true,"targetName":"4Z18"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetPdbId").value("4Z18"))
				.andExpect(jsonPath("$.ecNumber").value(nullValue()));

		files.verify();
	}

	@Test
	void pastedEnzymePdbPersistsEcNumber() throws Exception {
		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/2PTN/1", String.class))
				.thenReturn(ENZYME_API);

		mockMvc.perform(post("/api/projects")
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"trypsin","target":%s}
								""".formatted(jsonString(pdb("2PTN", "COMPND   5 EC: 3.4.21.4;")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.targetPdbId").value("2PTN"))
				.andExpect(jsonPath("$.ecNumber").value("3.4.21.4"))
				.andExpect(jsonPath("$.ecLineage").value("trypsin"));

		Project stored = projectRepository.findAll().stream()
				.filter(p -> "trypsin".equals(p.getName()))
				.findFirst()
				.orElseThrow();
		mockMvc.perform(get("/api/projects/{id}", stored.getId())
						.header("Authorization", "Bearer " + token("ROLE_USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetPdbId").value("2PTN"))
				.andExpect(jsonPath("$.ecNumber").value("3.4.21.4"))
				.andExpect(jsonPath("$.ecLineage").doesNotExist());
	}

	@Test
	void pastedPdL1LeavesEcNull() throws Exception {
		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/4Z18/1", String.class))
				.thenReturn("{\"rcsb_polymer_entity\":{}}");

		mockMvc.perform(post("/api/projects")
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"pdl1-paste","target":%s}
								""".formatted(jsonString(pdb("4Z18", "COMPND   2 MOLECULE: PROGRAMMED CELL DEATH 1 LIGAND 1;")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetPdbId").value("4Z18"))
				.andExpect(jsonPath("$.ecNumber").value(nullValue()));
	}

	@Test
	void unreachableDataApiStillCreatesProjectWithinTimeout() throws Exception {
		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/2PTN/1", String.class))
				.thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

		long start = System.nanoTime();
		mockMvc.perform(post("/api/projects")
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"enzyme-offline","target":%s}
								""".formatted(jsonString(pdb("2PTN", "COMPND   5 EC: 3.4.21.4;")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetPdbId").value("2PTN"))
				.andExpect(jsonPath("$.ecNumber").value("3.4.21.4"));
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		assertTrue(elapsedMs < 4_000, "create returned in " + elapsedMs + "ms");
	}

	@Test
	void metadataFailureDoesNotAbortCreate() throws Exception {
		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/2PTN/1", String.class))
				.thenThrow(new Error("metadata boom"));

		mockMvc.perform(post("/api/projects")
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"metadata-boom","target":%s}
								""".formatted(jsonString(pdb("2PTN", "COMPND   5 EC: 3.4.21.4;")))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetPdbId").value(nullValue()))
				.andExpect(jsonPath("$.ecNumber").value(nullValue()));
	}

	@Test
	void adminBackfillResolvesLegacyProjectAndIsIdempotent() throws Exception {
		Project legacy = new Project();
		legacy.setName("legacy-enzyme");
		legacy.setTarget(pdb("2PTN", "COMPND   5 EC: 3.4.21.4;"));
		legacy = projectRepository.save(legacy);
		assertNull(legacy.getTargetPdbId());
		assertNull(legacy.getEcNumber());

		when(rcsbDataRestTemplate.getForObject(
				"https://data.rcsb.org/rest/v1/core/polymer_entity/2PTN/1", String.class))
				.thenReturn(ENZYME_API);

		mockMvc.perform(post("/api/projects/{id}/resolve-ec", legacy.getId())
						.header("Authorization", "Bearer " + token("ROLE_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetPdbId").value("2PTN"))
				.andExpect(jsonPath("$.ecNumber").value("3.4.21.4"))
				.andExpect(jsonPath("$.ecLineage").doesNotExist());

		mockMvc.perform(post("/api/projects/{id}/resolve-ec", legacy.getId())
						.header("Authorization", "Bearer " + token("ROLE_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetPdbId").value("2PTN"))
				.andExpect(jsonPath("$.ecNumber").value("3.4.21.4"));

		Project stored = projectRepository.findById(legacy.getId()).orElseThrow();
		assertEquals("trypsin", stored.getEcLineage());
	}

	@Test
	void resolveEcIsAdminOnly() throws Exception {
		Project legacy = new Project();
		legacy.setName("legacy-locked");
		legacy.setTarget("ATOM");
		legacy = projectRepository.save(legacy);

		mockMvc.perform(post("/api/projects/{id}/resolve-ec", legacy.getId())
						.header("Authorization", "Bearer " + token("ROLE_USER")))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/projects/{id}/resolve-ec", legacy.getId()))
				.andExpect(status().isForbidden());
	}

	private static String pdb(String id, String compnd) {
		return "HEADER" + " ".repeat(56) + id + "\n" + compnd + "\n";
	}

	private static String jsonString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	private String token(String role) {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		Algorithm algorithm = Algorithm.HMAC512(secretBytes);
		return JWT.create()
				.withSubject("ec-metadata-test")
				.withClaim("roles", role)
				.sign(algorithm);
	}
}
