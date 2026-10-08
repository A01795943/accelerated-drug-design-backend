package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoreLoadPolicyTest {

	private static final CoreLoadPolicy.Thresholds ENTER = new CoreLoadPolicy.Thresholds(90, 90, 95, 90, 90);
	private static final CoreLoadPolicy.Thresholds RECOVER = new CoreLoadPolicy.Thresholds(80, 80, 80, 80, 85);

	@Test
	void crossesEntryThresholdOnAnyMetric() {
		CoreLoadPolicy.Decision decision = CoreLoadPolicy.decide("AVAILABLE", sample(91, 10, 10, 10, 10), ENTER, RECOVER);
		assertEquals("OVERLOADED", decision.status());
		assertEquals("cpu", decision.reason());
	}

	@Test
	void staysOverloadedUntilEveryMetricIsUnderRecovery() {
		CoreLoadPolicy.Decision still = CoreLoadPolicy.decide("OVERLOADED", sample(85, 10, 10, 10, 10), ENTER, RECOVER);
		assertEquals("OVERLOADED", still.status());

		CoreLoadPolicy.Decision recovered = CoreLoadPolicy.decide("OVERLOADED", sample(79, 10, 10, 10, 10), ENTER, RECOVER);
		assertEquals("AVAILABLE", recovered.status());
		assertNull(recovered.reason());
	}

	@Test
	void diskUsesHigherRecoveryThreshold() {
		CoreLoadPolicy.Decision still = CoreLoadPolicy.decide("OVERLOADED", sample(10, 10, 10, 10, 86), ENTER, RECOVER);
		assertEquals("OVERLOADED", still.status());
		CoreLoadPolicy.Decision recovered = CoreLoadPolicy.decide("OVERLOADED", sample(10, 10, 10, 10, 84), ENTER, RECOVER);
		assertEquals("AVAILABLE", recovered.status());
	}

	@Test
	void missingGpuDoesNotForceOverload() {
		Map<String, Object> metrics = Map.of(
				"cpu", Map.of("percent", 10),
				"memory", Map.of("percent", 10),
				"gpus", List.of(),
				"disk", Map.of("percent", 10));
		CoreLoadPolicy.Decision decision = CoreLoadPolicy.decide("AVAILABLE", metrics, ENTER, RECOVER);
		assertEquals("AVAILABLE", decision.status());
	}

	private static Map<String, Object> sample(double cpu, double mem, double gpuUtil, double gpuMem, double disk) {
		return Map.of(
				"cpu", Map.of("percent", cpu),
				"memory", Map.of("percent", mem),
				"gpus", List.of(Map.of("util_percent", gpuUtil, "mem_percent", gpuMem)),
				"disk", Map.of("percent", disk));
	}
}
