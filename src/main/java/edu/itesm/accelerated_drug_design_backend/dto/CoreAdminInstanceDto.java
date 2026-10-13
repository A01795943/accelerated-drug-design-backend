package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;
import java.util.List;

public record CoreAdminInstanceDto(
		Long id,
		String name,
		String baseUrl,
		boolean enabled,
		String status,
		String statusReason,
		int maxConcurrentRuns,
		long inFlightMinibatches,
		String healthState,
		String currentRunId,
		Instant lastMetricsAt,
		String lastMetricsJson,
		long inFlightTotal,
		String occupancy,
		List<RunningMinibatchDto> runningMinibatches,
		OtherRunDto otherRun,
		boolean gpuDataAvailable,
		List<GpuReading> gpus
) {}
