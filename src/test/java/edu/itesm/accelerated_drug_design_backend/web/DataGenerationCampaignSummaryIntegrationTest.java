package edu.itesm.accelerated_drug_design_backend.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.entity.*;
import edu.itesm.accelerated_drug_design_backend.repository.DataGenerationCampaignRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataGenerationCampaignSummaryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private DataGenerationCampaignRepository campaignRepository;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void listCampaignSummaries_returnsProgressOrderedByCreatedAtDesc() throws Exception {
		Project project = new Project();
		project.setName("summary-test-project");
		project = projectRepository.save(project);

		DataGenerationCampaign older = saveCampaign(
				project,
				Instant.parse("2026-01-01T10:00:00Z"),
				DataGenerationCampaign.STATUS_RUNNING,
				List.of(CampaignBackbone.STATUS_COMPLETED, CampaignBackbone.STATUS_RUNNING),
				List.of(
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_RUNNING,
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_RUNNING,
						CampaignTemperatureJob.STATUS_RUNNING));

		DataGenerationCampaign newer = saveCampaign(
				project,
				Instant.parse("2026-01-02T10:00:00Z"),
				DataGenerationCampaign.STATUS_PARTIAL,
				List.of(CampaignBackbone.STATUS_COMPLETED, CampaignBackbone.STATUS_COMPLETED),
				List.of(
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_COMPLETED,
						CampaignTemperatureJob.STATUS_RUNNING,
						CampaignTemperatureJob.STATUS_RUNNING));

		mockMvc.perform(get("/api/projects/{projectId}/data-generation-campaigns", project.getId())
						.header("Authorization", "Bearer " + bearerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(newer.getId()))
				.andExpect(jsonPath("$[0].name").value(nullValue()))
				.andExpect(jsonPath("$[0].status").value("PARTIAL"))
				.andExpect(jsonPath("$[0].totalBackbones").value(2))
				.andExpect(jsonPath("$[0].completedBackbones").value(2))
				.andExpect(jsonPath("$[0].totalTemperatureJobs").value(6))
				.andExpect(jsonPath("$[0].completedTemperatureJobs").value(4))
				.andExpect(jsonPath("$[0].totalSteps").value(8))
				.andExpect(jsonPath("$[0].completedSteps").value(6))
				.andExpect(jsonPath("$[0].progressPercent").value(75.0))
				.andExpect(jsonPath("$[1].id").value(older.getId()))
				.andExpect(jsonPath("$[1].status").value("RUNNING"))
				.andExpect(jsonPath("$[1].totalBackbones").value(2))
				.andExpect(jsonPath("$[1].completedBackbones").value(1))
				.andExpect(jsonPath("$[1].totalTemperatureJobs").value(6))
				.andExpect(jsonPath("$[1].completedTemperatureJobs").value(3))
				.andExpect(jsonPath("$[1].totalSteps").value(8))
				.andExpect(jsonPath("$[1].completedSteps").value(4))
				.andExpect(jsonPath("$[1].progressPercent").value(50.0));
	}

	private DataGenerationCampaign saveCampaign(
			Project project,
			Instant createdAt,
			String status,
			List<String> backboneStatuses,
			List<String> temperatureJobStatuses) {
		DataGenerationCampaign campaign = new DataGenerationCampaign();
		campaign.setProject(project);
		campaign.setStatus(status);
		campaign.setCreatedAt(createdAt);
		campaign.setNumSeqsPerTemperature(8);
		campaign.setTemperaturesJson("[0.1,0.2,0.3]");

		for (int i = 0; i < backboneStatuses.size(); i++) {
			CampaignBackbone backbone = new CampaignBackbone();
			backbone.setCampaign(campaign);
			backbone.setSortOrder(i);
			backbone.setContigs("20-35/0 A1-100");
			backbone.setStatus(backboneStatuses.get(i));
			campaign.getBackbones().add(backbone);

			List<CampaignTemperatureJob> jobs = new ArrayList<>();
			for (int j = 0; j < 3; j++) {
				int idx = i * 3 + j;
				CampaignTemperatureJob job = new CampaignTemperatureJob();
				job.setCampaignBackbone(backbone);
				job.setTemperature(0.1 + (j * 0.1));
				job.setStatus(temperatureJobStatuses.get(idx));
				jobs.add(job);
			}
			backbone.setTemperatureJobs(jobs);
		}

		return campaignRepository.save(campaign);
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
