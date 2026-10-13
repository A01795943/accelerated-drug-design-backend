package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * One GPU from the core's {@code /metrics/system} payload. Missing fields stay null
 * so an older core that omits VRAM does not look like 0%.
 */
public record GpuReading(
		Integer index,
		String name,
		Double utilPercent,
		Double memUsedMb,
		Double memTotalMb,
		Double memPercent,
		Double temperatureC) {
}
