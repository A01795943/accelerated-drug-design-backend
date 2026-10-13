package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpnnSamplingTest {

	/** Same stride as pipeline/mpnn_seed.py SEED_STRIDE. */
	private static final int SEED_STRIDE = 1_000_003;

	@Test
	void minibatchWindowsAndNeighborJobsDoNotShareSeeds() {
		int batch = 100;
		Set<Integer> firstJob = seeds(10L, 0, 20, batch);
		Set<Integer> laterBatches = seeds(10L, 2000, 20, batch);
		Set<Integer> nextTemperature = seeds(11L, 0, 20, batch);

		assertEquals(20 * batch, firstJob.size());
		assertTrue(disjoint(firstJob, laterBatches));
		assertTrue(disjoint(firstJob, nextTemperature));
		assertTrue(disjoint(laterBatches, nextTemperature));
	}

	@Test
	void binderKeyIsTheTailAfterTheLastSlash() {
		String target = "A".repeat(72);
		String binderA = "CCCCCCCCCCCCCC";
		String binderB = "DDDDDDDDDDDDDD";
		assertEquals(14, binderA.length());
		assertEquals(binderA, MpnnSampling.binderKey(target + "/" + binderA));
		assertEquals(binderB, MpnnSampling.binderKey(target + "/" + binderB));
		assertEquals(binderA, MpnnSampling.binderKey("B".repeat(72) + "/" + binderA));
		assertEquals(binderA, MpnnSampling.binderKey("  " + target.toLowerCase() + " / " + binderA.toLowerCase() + " \n"));
		assertEquals("ACDE", MpnnSampling.binderKey(" ac de "));
		assertEquals("", MpnnSampling.binderKey(null));
	}

	private static Set<Integer> seeds(long jobId, int nOffset, int count, int batch) {
		Set<Integer> seeds = new HashSet<>();
		for (int batchIndex = 0; batchIndex < count; batchIndex++) {
			int base = MpnnSampling.seedFor(jobId, nOffset + batchIndex * batch);
			for (int n = 0; n < batch; n++) {
				seeds.add(base + n * SEED_STRIDE);
			}
		}
		return seeds;
	}

	private static boolean disjoint(Set<Integer> left, Set<Integer> right) {
		for (Integer seed : left) {
			if (right.contains(seed)) {
				return false;
			}
		}
		return true;
	}
}
