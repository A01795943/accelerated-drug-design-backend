package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;
import edu.itesm.accelerated_drug_design_backend.dto.GenerateSuggestionsResponse;

public interface RfdiffusionParamService {
	GenerateResult generateForProject(Long projectId);

	GenerateSuggestionsResponse generateNForProject(Long projectId, int count, int minLength, int maxLength);
}
