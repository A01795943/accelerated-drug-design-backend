package edu.itesm.accelerated_drug_design_backend.repository;

import java.time.Instant;

public interface RunningOccupancyRow {
	Long getInstanceId();
	Long getMinibatchId();
	Long getGenerationJobId();
	Integer getIdx();
	Integer getNOffset();
	Integer getNumSeqs();
	Instant getStartedAt();
	Double getTemperature();
	Long getBackboneId();
	String getBackboneName();
	Long getProjectId();
}
