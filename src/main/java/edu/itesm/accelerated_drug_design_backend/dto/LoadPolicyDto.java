package edu.itesm.accelerated_drug_design_backend.dto;

public record LoadPolicyDto(
		double cpuEnter,
		double memoryEnter,
		double gpuUtilEnter,
		double gpuMemEnter,
		double diskEnter,
		double cpuRecover,
		double memoryRecover,
		double gpuUtilRecover,
		double gpuMemRecover,
		double diskRecover) {
}
