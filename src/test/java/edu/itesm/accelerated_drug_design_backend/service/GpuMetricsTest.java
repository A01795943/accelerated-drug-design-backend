package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GpuReading;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuMetricsTest {

	@Test
	void readsEveryGpuAndPicksTheBusiestMemory() {
		List<GpuReading> gpus = GpuMetrics.read(List.of(
				Map.of("index", 0, "name", "a", "util_percent", 10, "mem_used_mb", 1000, "mem_total_mb", 16000, "mem_percent", 6, "temperature_c", 40),
				Map.of("index", 1, "name", "b", "util_percent", 80, "mem_used_mb", 12000, "mem_total_mb", 16000, "mem_percent", 75, "temperature_c", 70)));
		assertEquals(2, gpus.size());
		assertEquals("b", gpus.get(1).name());
		assertEquals(70, gpus.get(1).temperatureC());
		GpuReading busiest = GpuMetrics.busiestMemory(gpus);
		assertEquals(1, busiest.index());
		assertEquals(12000, busiest.memUsedMb());
		assertEquals(16000, busiest.memTotalMb());
	}

	@Test
	void emptyOrMissingGpusAreNotZeroPercent() {
		assertFalse(GpuMetrics.hasData(null));
		assertFalse(GpuMetrics.hasData(List.of()));
		assertTrue(GpuMetrics.read(null).isEmpty());
		assertNull(GpuMetrics.busiestMemory(List.of()));
		assertNull(GpuMetrics.busiestMemory(GpuMetrics.read(List.of(Map.of("index", 0, "util_percent", 0)))));
	}
}
