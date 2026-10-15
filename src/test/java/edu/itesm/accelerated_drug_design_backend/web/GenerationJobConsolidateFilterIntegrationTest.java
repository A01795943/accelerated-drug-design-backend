package edu.itesm.accelerated_drug_design_backend.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.csv.CsvRecords;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GenerationJobConsolidateFilterIntegrationTest {

	private static final String PDB_MARKER = "PDB_MARKER_DO_NOT_EXPORT";
	private static final String FASTA_MARKER = "FASTA_MARKER_DO_NOT_EXPORT";
	private static final String BEST_PDB_MARKER = "BESTPDB_MARKER_DO_NOT_EXPORT";
	private static final String OUTPUT_CSV_MARKER = "OUTPUTCSV_MARKER_DO_NOT_EXPORT";
	private static final String STRUCTURE_MARKER = "STRUCTURE_MARKER_DO_NOT_EXPORT";
	private static final String RUNNING_SEQ = "SHOULD_NOT_EXPORT_RUNNING";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private BackboneRepository backboneRepository;
	@Autowired
	private GenerationJobRepository generationJobRepository;
	@Autowired
	private GenerationJobRecordRepository recordRepository;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void backboneFilter_isPagedOnTheServer() throws Exception {
		Project project = saveProject();
		Backbone alpha = saveBackbone(project, "Alpha", "20-35/0 A1-100", "A171,A174,A176", "B", 3);
		Backbone beta = saveBackbone(project, "Beta", "10-20", "B10", null, 9);
		Backbone unused = saveBackbone(project, "Unused", "1-2", "C1", null, 1);

		GenerationJob low = saveJob(project, alpha, GenerationJob.STATUS_COMPLETED, 0.1);
		GenerationJob high = saveJob(project, alpha, GenerationJob.STATUS_COMPLETED, 0.5);
		saveJob(project, alpha, GenerationJob.STATUS_RUNNING, 0.9);
		saveJob(project, beta, GenerationJob.STATUS_COMPLETED, 0.4);

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs", project.getId())
						.param("backboneIds", alpha.getId().toString())
						.param("page", "0")
						.param("size", "1")
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(3))
				.andExpect(jsonPath("$.consolidatableCount").value(2))
				.andExpect(jsonPath("$.jobs", hasSize(1)))
				.andExpect(jsonPath("$.jobs[0].backboneId").value(alpha.getId()));

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs", project.getId())
						.param("backboneIds", alpha.getId().toString())
						.param("page", "1")
						.param("size", "1")
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobs", hasSize(1)))
				.andExpect(jsonPath("$.jobs[0].backboneId").value(alpha.getId()))
				.andExpect(jsonPath("$.totalElements").value(3));

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs", project.getId())
						.param("backboneIds", alpha.getId().toString())
						.param("sort", "temperature")
						.param("direction", "asc")
						.param("size", "10")
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobs", hasSize(3)))
				.andExpect(jsonPath("$.jobs[0].id").value(low.getId()))
				.andExpect(jsonPath("$.jobs[1].id").value(high.getId()));

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs", project.getId())
						.param("backboneIds", beta.getId().toString())
						.param("size", "10")
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.consolidatableCount").value(1))
				.andExpect(jsonPath("$.jobs[0].backboneId").value(beta.getId()));

		mockMvc.perform(get("/api/projects/{projectId}/generation-jobs/filter-backbones", project.getId())
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(alpha.getId()))
				.andExpect(jsonPath("$[0].name").value("Alpha"))
				.andExpect(jsonPath("$[1].id").value(beta.getId()))
				.andExpect(jsonPath("$[1].name").value("Beta"));

		assertTrue(unused.getId() > 0);
	}

	@Test
	void consolidate_selectAllFiltered_andMixedBackbones() throws Exception {
		Project project = saveProject();
		Backbone alpha = saveBackbone(project, "Alpha", "20-35/0 A1-100", "A171,A174,A176", "B", 3);
		Backbone beta = saveBackbone(project, "Beta", "10-20", "B10", null, 9);

		GenerationJob alphaLow = saveJob(project, alpha, GenerationJob.STATUS_COMPLETED, 0.1);
		GenerationJob alphaHigh = saveJob(project, alpha, GenerationJob.STATUS_COMPLETED, 0.5);
		GenerationJob running = saveJob(project, alpha, GenerationJob.STATUS_RUNNING, 0.9);
		GenerationJob betaJob = saveJob(project, beta, GenerationJob.STATUS_COMPLETED, 0.4);

		saveRecord(alphaLow, 0, "SEQ_A0", "0.1,0.2", "mpnn-a");
		saveRecord(alphaLow, 1, "SEQ_A1", "pae-text", "mpnn-a");
		saveRecord(alphaHigh, 0, "SEQ_A2", "pae-b", "1.5,2.0");
		saveRecord(running, 0, RUNNING_SEQ, "pae-run", "mpnn-run");
		saveRecord(betaJob, 0, "SEQ_B0", "pae-beta", "mpnn-b");

		String filtered = csv(project.getId(), selectAllJson(List.of(alpha.getId())));
		assertNoLongText(filtered);
		List<List<String>> filteredRows = CsvRecords.parse(filtered);
		assertEquals(header(), filteredRows.get(0));
		assertEquals(3, filteredRows.size() - 1);
		for (int i = 1; i < filteredRows.size(); i++) {
			List<String> row = filteredRows.get(i);
			assertEquals(14, row.size());
			assertEquals(alpha.getId().toString(), row.get(0));
			assertEquals("20-35/0 A1-100", row.get(1));
			assertEquals("A171,A174,A176", row.get(2));
			assertEquals("B", row.get(3));
			assertEquals("3", row.get(4));
			assertFalse(row.get(13).equals(RUNNING_SEQ));
		}
		assertTrue(filteredRows.stream().anyMatch(r -> r.size() > 13 && "SEQ_A0".equals(r.get(13))));
		assertTrue(filteredRows.stream().anyMatch(r -> r.size() > 13 && "SEQ_A1".equals(r.get(13))));
		assertTrue(filteredRows.stream().anyMatch(r -> r.size() > 13 && "SEQ_A2".equals(r.get(13))));
		assertTrue(filteredRows.stream().anyMatch(r -> r.size() > 8 && "0.1,0.2".equals(r.get(8))));
		assertTrue(filteredRows.stream().anyMatch(r -> r.size() > 12 && "1.5,2.0".equals(r.get(12))));

		String mixed = csv(project.getId(), explicitJson(List.of(alphaLow.getId(), betaJob.getId())));
		assertNoLongText(mixed);
		List<List<String>> mixedRows = CsvRecords.parse(mixed);
		assertEquals(4, mixedRows.size());
		List<String> alphaRow = mixedRows.stream().filter(r -> "SEQ_A0".equals(r.get(13))).findFirst().orElseThrow();
		List<String> betaRow = mixedRows.stream().filter(r -> "SEQ_B0".equals(r.get(13))).findFirst().orElseThrow();
		assertEquals(alpha.getId().toString(), alphaRow.get(0));
		assertEquals("A171,A174,A176", alphaRow.get(2));
		assertEquals("3", alphaRow.get(4));
		assertEquals("0.1", alphaRow.get(5));
		assertEquals(beta.getId().toString(), betaRow.get(0));
		assertEquals("10-20", betaRow.get(1));
		assertEquals("B10", betaRow.get(2));
		assertEquals("", betaRow.get(3));
		assertEquals("9", betaRow.get(4));
		assertEquals("0.4", betaRow.get(5));

		mockMvc.perform(post("/api/projects/{projectId}/generation-jobs/consolidated-csv", project.getId())
						.header("Authorization", "Bearer " + bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(explicitJson(List.of(running.getId()))))
				.andExpect(status().isBadRequest());
	}

	private static String selectAllJson(List<Long> backboneIds) {
		return "{\"selectAllFiltered\":true,\"backboneIds\":[" + joinIds(backboneIds) + "]}";
	}

	private static String explicitJson(List<Long> jobIds) {
		return "{\"jobIds\":[" + joinIds(jobIds) + "]}";
	}

	private static String joinIds(List<Long> ids) {
		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	private static List<String> header() {
		return List.of(
				"backbone_id", "contigs", "hotspots", "chains_to_remove", "backbone_iterations",
				"temperature", "ptm", "i_ptm", "pae", "i_pae", "plddt", "rmsd", "mpnn", "seq");
	}

	private String csv(Long projectId, String json) throws Exception {
		return mockMvc.perform(post("/api/projects/{projectId}/generation-jobs/consolidated-csv", projectId)
						.header("Authorization", "Bearer " + bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	private static void assertNoLongText(String csv) {
		assertFalse(csv.contains(PDB_MARKER));
		assertFalse(csv.contains(FASTA_MARKER));
		assertFalse(csv.contains(BEST_PDB_MARKER));
		assertFalse(csv.contains(OUTPUT_CSV_MARKER));
		assertFalse(csv.contains(STRUCTURE_MARKER));
		assertFalse(csv.contains(RUNNING_SEQ));
	}

	private Project saveProject() {
		Project project = new Project();
		project.setName("csv-" + System.nanoTime());
		return projectRepository.saveAndFlush(project);
	}

	private Backbone saveBackbone(Project project, String name, String contigs, String hotspots,
			String chainsToRemove, int iterations) {
		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setName(name);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setContigs(contigs);
		backbone.setHotspots(hotspots);
		backbone.setChainsToRemove(chainsToRemove);
		backbone.setIterations(iterations);
		backbone.setStructure(STRUCTURE_MARKER);
		return backboneRepository.saveAndFlush(backbone);
	}

	private GenerationJob saveJob(Project project, Backbone backbone, String status, double temperature) {
		GenerationJob job = new GenerationJob();
		job.setProject(project);
		job.setBackbone(backbone);
		job.setRunId("run-" + System.nanoTime());
		job.setStatus(status);
		job.setTemperature(temperature);
		job.setNumSeqs(2);
		job.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		job.setFasta(FASTA_MARKER);
		job.setBestPdb(BEST_PDB_MARKER);
		job.setOutputCsv(OUTPUT_CSV_MARKER);
		return generationJobRepository.saveAndFlush(job);
	}

	private void saveRecord(GenerationJob job, int n, String seq, String pae, String mpnn) {
		GenerationJobRecord record = new GenerationJobRecord();
		record.setGenerationJobId(job.getId());
		record.setN(n);
		record.setSeq(seq);
		record.setPae(pae);
		record.setIPae("i-pae-text");
		record.setMpnn(mpnn);
		record.setPtm(0.8);
		record.setIPtm(0.7);
		record.setPlddt(90.0);
		record.setRmsd(1.2);
		record.setPdb(PDB_MARKER);
		recordRepository.saveAndFlush(record);
	}

	private String bearerToken() {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		Algorithm algorithm = Algorithm.HMAC512(secretBytes);
		return JWT.create()
				.withSubject("integration-test")
				.withClaim("roles", "ROLE_USER")
				.sign(algorithm);
	}
}
