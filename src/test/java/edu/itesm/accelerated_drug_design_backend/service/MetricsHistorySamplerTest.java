package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CoreInstanceMetricsHistoryDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsHistorySamplerTest {

	@Test
	void downsampleStaysWithinThePointCapAndKeepsNullMemoryAsNull() {
		Instant start = Instant.parse("2026-10-13T00:00:00Z");
		List<CoreInstanceMetricsHistoryDto> points = new ArrayList<>();
		for (int i = 0; i < 600; i++) {
			Double used = i < 10 ? null : (double) i;
			points.add(new CoreInstanceMetricsHistoryDto(
					(long) i, 1L, start.plusSeconds(i),
					50.0, 40.0, 10.0, 20.0, 30.0, 0, 0.0, 0.0, used, used == null ? null : 16000.0));
		}
		List<CoreInstanceMetricsHistoryDto> reduced = MetricsHistorySampler.limit(points);
		assertTrue(reduced.size() <= MetricsHistorySampler.MAX_POINTS);
		assertTrue(reduced.size() > 1);
		assertNull(reduced.get(0).gpuMemUsedMb());
		assertEquals(16000.0, reduced.get(reduced.size() - 1).gpuMemTotalMb());
	}

	@Test
	void aShortSeriesIsReturnedUnchanged() {
		CoreInstanceMetricsHistoryDto row = new CoreInstanceMetricsHistoryDto(
				1L, 1L, Instant.parse("2026-10-13T00:00:00Z"),
				1.0, 1.0, null, null, 1.0, null, null, null, null, null);
		assertEquals(List.of(row), MetricsHistorySampler.limit(List.of(row)));
		assertNull(row.gpuMemUsedMb());
	}
}
