package edu.itesm.accelerated_drug_design_backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import edu.itesm.accelerated_drug_design_backend.service.DataGenerationCampaignService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateDataGenerationCampaignRequestValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	void numBackbones_absent_isValid_legacyDefaultHandledInService() {
		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg = validConfig();
		cfg.setNumBackbones(null);

		Set<ConstraintViolation<CreateDataGenerationCampaignRequest.BackboneConfigDto>> violations =
				validator.validate(cfg);

		assertTrue(violations.isEmpty());
		assertEquals(1, DataGenerationCampaignService.resolveNumBackbones(cfg));
	}

	@Test
	void numBackbones_belowOne_isRejected() {
		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg = validConfig();
		cfg.setNumBackbones(0);

		Set<ConstraintViolation<CreateDataGenerationCampaignRequest.BackboneConfigDto>> violations =
				validator.validate(cfg);

		assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("numBackbones")));
	}

	@Test
	void numBackbones_aboveTwenty_isRejected() {
		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg = validConfig();
		cfg.setNumBackbones(21);

		Set<ConstraintViolation<CreateDataGenerationCampaignRequest.BackboneConfigDto>> violations =
				validator.validate(cfg);

		assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("numBackbones")));
	}

	private static CreateDataGenerationCampaignRequest.BackboneConfigDto validConfig() {
		CreateDataGenerationCampaignRequest.BackboneConfigDto cfg =
				new CreateDataGenerationCampaignRequest.BackboneConfigDto();
		cfg.setContigs("20-35/0 A1-100");
		cfg.setNumBackbones(2);
		return cfg;
	}
}
