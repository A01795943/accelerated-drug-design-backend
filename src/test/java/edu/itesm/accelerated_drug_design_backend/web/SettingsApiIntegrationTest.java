package edu.itesm.accelerated_drug_design_backend.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.dto.CreateGenerationJobRequest;
import edu.itesm.accelerated_drug_design_backend.dto.ProteinMpnnRunRequest;
import edu.itesm.accelerated_drug_design_backend.entity.Backbone;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJob;
import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneRepository;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobMinibatchRepository;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import edu.itesm.accelerated_drug_design_backend.service.GenerationJobService;
import edu.itesm.accelerated_drug_design_backend.service.MinibatchDispatcher;
import edu.itesm.accelerated_drug_design_backend.service.SettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettingsApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private SettingsService settingsService;
	@Autowired
	private MinibatchDispatcher dispatcher;
	@Autowired
	private GenerationJobService generationJobService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private BackboneRepository backboneRepository;
	@Autowired
	private CoreInstanceRepository coreInstanceRepository;
	@Autowired
	private GenerationJobMinibatchRepository minibatchRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private EntityManager entityManager;
	@MockitoBean
	private CoreSystemInterface coreSystem;
	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Test
	void adminCanReadSettingsAndAUserCannot() throws Exception {
		mockMvc.perform(get("/api/settings"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/settings").header("Authorization", "Bearer " + token("ROLE_USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/settings").header("Authorization", "Bearer " + token("ROLE_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].key").value(SettingsService.MAX_ATTEMPT_FACTOR))
				.andExpect(jsonPath("$[0].type").value("integer"))
				.andExpect(jsonPath("$[0].min").value(1))
				.andExpect(jsonPath("$[0].max").value(1000))
				.andExpect(jsonPath("$[0].defaultValue").value("20"))
				.andExpect(jsonPath("$[0].group").value("Generación"));
	}

	@Test
	void putRejectsOutOfRangeAndUnknownKeys() throws Exception {
		mockMvc.perform(put("/api/settings/" + SettingsService.MAX_ATTEMPT_FACTOR)
						.header("Authorization", "Bearer " + token("ROLE_USER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"value\":\"30\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/settings/" + SettingsService.MAX_ATTEMPT_FACTOR)
						.header("Authorization", "Bearer " + token("ROLE_ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"value\":\"1001\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(
						"El valor de generation.max_attempt_factor debe ser un entero entre 1 y 1000."));
		mockMvc.perform(put("/api/settings/does.not.exist")
						.header("Authorization", "Bearer " + token("ROLE_ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"value\":\"1\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void savedFactorIsWhatTheNextDispatchSends() throws Exception {
		mockMvc.perform(put("/api/settings/" + SettingsService.MAX_ATTEMPT_FACTOR)
						.header("Authorization", "Bearer " + token("ROLE_ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"value\":\"15\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.value").value("15"))
				.andExpect(jsonPath("$.updatedBy").value("settings-admin"));
		assertEquals(15, settingsService.maxAttemptFactor());

		releaseSharedCores();
		GenerationJob job = createJob(4);
		dispatcher.dispatchPending();

		ArgumentCaptor<ProteinMpnnRunRequest> request = ArgumentCaptor.forClass(ProteinMpnnRunRequest.class);
		verify(coreSystem).triggerMpnn(any(), request.capture());
		assertEquals(60, request.getValue().getMaxAttempts());
		assertEquals(4, job.getNumSeqs());
	}

	private void releaseSharedCores() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			for (CoreInstance instance : coreInstanceRepository.findAll()) {
				instance.setStatus(CoreInstance.STATUS_AVAILABLE);
				instance.setStatusReason(null);
				instance.setConsecutiveFailures(0);
			}
			for (GenerationJobMinibatch existing : minibatchRepository.findAll()) {
				if (GenerationJobMinibatch.STATUS_PENDING.equals(existing.getStatus())
						|| GenerationJobMinibatch.STATUS_RUNNING.equals(existing.getStatus())) {
					existing.setStatus(GenerationJobMinibatch.STATUS_ERROR);
					existing.setCoreInstanceId(null);
				}
			}
		});
		entityManager.clear();
	}

	private GenerationJob createJob(int numSeqs) {
		Project project = new Project();
		project.setName("settings-" + UUID.randomUUID());
		project.setTarget("ATOM 1");
		project = projectRepository.save(project);
		Backbone backbone = new Backbone();
		backbone.setProject(project);
		backbone.setStatus(Backbone.STATUS_COMPLETED);
		backbone.setStructure("ATOM      1  N   ALA A   1      11.000  12.000  13.000  1.00  0.00           N");
		backbone.setContigs("A1-10");
		backbone = backboneRepository.save(backbone);
		CreateGenerationJobRequest request = new CreateGenerationJobRequest();
		request.setBackboneId(backbone.getId());
		request.setTemperature(0.4);
		request.setNumSeqs(numSeqs);
		return generationJobService.createJob(project.getId(), request);
	}

	private String token(String role) {
		byte[] secretBytes = Base64.getDecoder().decode(jwtSecret);
		String subject = "ROLE_ADMIN".equals(role) ? "settings-admin" : "settings-user";
		return JWT.create().withSubject(subject).withClaim("roles", role).sign(Algorithm.HMAC512(secretBytes));
	}
}
