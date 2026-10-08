package edu.itesm.accelerated_drug_design_backend.dto;

import java.time.Instant;

public record CoreInstanceMetricsHistoryDto(
		Long id,
		Long coreInstanceId,
		Instant collectedAt,
		Double cpuPercent,
		Double memPercent,
		Double gpuUtilPercent,
		Double gpuMemPercent,
		Double diskPercent,
		Integer runningCount,
		Double netSentPerSec,
		Double netRecvPerSec
) {}
