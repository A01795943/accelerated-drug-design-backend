package edu.itesm.accelerated_drug_design_backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hysteresis for core load status. A missing sample (no GPU) is ignored.
 * Entry uses strict {@code >} threshold. Recovery requires every present sample
 * to be strictly below its recovery threshold.
 */
public final class CoreLoadPolicy {

	public record Thresholds(double cpu, double memory, double gpuUtil, double gpuMem, double disk) {}

	public record Decision(String status, String reason) {}

	private CoreLoadPolicy() {}

	public static Decision decide(String previousStatus, Map<String, Object> metrics, Thresholds enter, Thresholds recover) {
		Double cpu = nestedPercent(metrics, "cpu");
		Double memory = nestedPercent(metrics, "memory");
		Double gpuUtil = maxGpu(metrics, "util_percent");
		Double gpuMem = maxGpu(metrics, "mem_percent");
		Double disk = nestedPercent(metrics, "disk");

		List<String> hot = new ArrayList<>();
		if (above(cpu, enter.cpu())) hot.add("cpu");
		if (above(memory, enter.memory())) hot.add("memory");
		if (above(gpuUtil, enter.gpuUtil())) hot.add("gpu_util");
		if (above(gpuMem, enter.gpuMem())) hot.add("gpu_mem");
		if (above(disk, enter.disk())) hot.add("disk");
		if (!hot.isEmpty()) {
			return new Decision("OVERLOADED", String.join(",", hot));
		}

		boolean recovering = "OVERLOADED".equals(previousStatus);
		if (recovering) {
			List<String> stillHigh = new ArrayList<>();
			if (!below(cpu, recover.cpu())) stillHigh.add("cpu");
			if (!below(memory, recover.memory())) stillHigh.add("memory");
			if (!below(gpuUtil, recover.gpuUtil())) stillHigh.add("gpu_util");
			if (!below(gpuMem, recover.gpuMem())) stillHigh.add("gpu_mem");
			if (!below(disk, recover.disk())) stillHigh.add("disk");
			if (!stillHigh.isEmpty()) {
				return new Decision("OVERLOADED", "recovering:" + String.join(",", stillHigh));
			}
		}
		return new Decision("AVAILABLE", null);
	}

	static Double nestedPercent(Map<String, Object> metrics, String objectKey) {
		if (metrics == null) {
			return null;
		}
		Object node = metrics.get(objectKey);
		if (!(node instanceof Map<?, ?> map)) {
			return null;
		}
		return asDouble(map.get("percent"));
	}

	@SuppressWarnings("unchecked")
	static Double maxGpu(Map<String, Object> metrics, String field) {
		if (metrics == null) {
			return null;
		}
		Object gpus = metrics.get("gpus");
		if (!(gpus instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		Double max = null;
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> map)) {
				continue;
			}
			Double value = asDouble(map.get(field));
			if (value == null) {
				continue;
			}
			max = max == null ? value : Math.max(max, value);
		}
		return max;
	}

	private static boolean above(Double value, double threshold) {
		return value != null && value > threshold;
	}

	private static boolean below(Double value, double threshold) {
		return value == null || value < threshold;
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
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
