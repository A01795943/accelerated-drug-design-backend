package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceMetricsHistoryDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a history response at about {@link #MAX_POINTS} by averaging equal time
 * buckets. A bucket whose samples are all null stays null, so a missing VRAM
 * reading is a gap and not a zero.
 */
public final class MetricsHistorySampler {

	public static final int MAX_POINTS = 300;

	private MetricsHistorySampler() {
	}

	public static List<CoreInstanceMetricsHistoryDto> limit(List<CoreInstanceMetricsHistoryDto> points) {
		if (points == null || points.size() <= MAX_POINTS) {
			return points == null ? List.of() : points;
		}
		long start = points.get(0).collectedAt().toEpochMilli();
		long end = points.get(points.size() - 1).collectedAt().toEpochMilli();
		long span = Math.max(1L, end - start);
		long bucketMs = Math.max(1L, (span + MAX_POINTS - 1) / MAX_POINTS);
		List<CoreInstanceMetricsHistoryDto> reduced = new ArrayList<>();
		List<CoreInstanceMetricsHistoryDto> bucket = new ArrayList<>();
		long bucketIndex = 0;
		for (CoreInstanceMetricsHistoryDto point : points) {
			long index = Math.min(MAX_POINTS - 1, (point.collectedAt().toEpochMilli() - start) / bucketMs);
			if (!bucket.isEmpty() && index != bucketIndex) {
				reduced.add(average(bucket));
				bucket = new ArrayList<>();
			}
			bucketIndex = index;
			bucket.add(point);
		}
		if (!bucket.isEmpty()) {
			reduced.add(average(bucket));
		}
		return reduced;
	}

	private static CoreInstanceMetricsHistoryDto average(List<CoreInstanceMetricsHistoryDto> bucket) {
		CoreInstanceMetricsHistoryDto first = bucket.get(0);
		return new CoreInstanceMetricsHistoryDto(
				first.id(),
				first.coreInstanceId(),
				first.collectedAt(),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::cpuPercent).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::memPercent).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::gpuUtilPercent).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::gpuMemPercent).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::diskPercent).toList()),
				meanInt(bucket.stream().map(CoreInstanceMetricsHistoryDto::runningCount).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::netSentPerSec).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::netRecvPerSec).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::gpuMemUsedMb).toList()),
				mean(bucket.stream().map(CoreInstanceMetricsHistoryDto::gpuMemTotalMb).toList()));
	}

	private static Double mean(List<Double> values) {
		double sum = 0;
		int count = 0;
		for (Double value : values) {
			if (value != null) {
				sum += value;
				count++;
			}
		}
		return count == 0 ? null : sum / count;
	}

	private static Integer meanInt(List<Integer> values) {
		Double mean = mean(values.stream().map(value -> value == null ? null : value.doubleValue()).toList());
		return mean == null ? null : (int) Math.round(mean);
	}
}
