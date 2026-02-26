package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;

public interface RfdiffusionParamService {
    GenerateResult generateForProject(Long projectId);
}