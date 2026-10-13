package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GpuReading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@code gpus} array the core already returns. History stores a single
 * pair of megabytes: the GPU with the greatest {@code mem_used_mb}. Ties go to
 * the higher {@code mem_percent}, then the lower index. The live screen still
 * lists every GPU. An empty or missing array is "no GPU data", not 0%.
 */
public final class GpuMetrics {

	private GpuMetrics() {
	}

	public static boolean hasData(Object gpusNode) {
		return gpusNode instanceof List<?> list && !list.isEmpty();
	}

	public static List<GpuReading> read(Object gpusNode) {
		if (!(gpusNode instanceof List<?> list)) {
			return List.of();
		}
		List<GpuReading> readings = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> map)) {
				continue;
			}
			readings.add(new GpuReading(
					asInt(map.get("index")),
					map.get("name") == null ? null : String.valueOf(map.get("name")),
					asDouble(map.get("util_percent")),
					asDouble(map.get("mem_used_mb")),
					asDouble(map.get("mem_total_mb")),
					asDouble(map.get("mem_percent")),
					asDouble(map.get("temperature_c"))));
		}
		return List.copyOf(readings);
	}

	public static GpuReading busiestMemory(List<GpuReading> gpus) {
		if (gpus == null || gpus.isEmpty()) {
			return null;
		}
		return gpus.stream()
				.filter(gpu -> gpu.memUsedMb() != null)
				.max(Comparator
						.comparingDouble(GpuReading::memUsedMb)
						.thenComparing(gpu -> gpu.memPercent() == null ? Double.NEGATIVE_INFINITY : gpu.memPercent())
						.thenComparing(gpu -> gpu.index() == null ? Integer.MAX_VALUE : gpu.index(), Comparator.reverseOrder()))
				.orElse(null);
	}

	private static Double asDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value == null) {
			return null;
		}
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer asInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			return null;
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
