package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpnnSamplingTest {

	@Test
	void seedForIs31BitsDeterministicAndAlmostUnique() {
		Set<Integer> seeds = new HashSet<>();
		int collisions = 0;
		for (long jobId = 1; jobId <= 1000; jobId++) {
			for (int batch = 0; batch < 20; batch++) {
				int seed = MpnnSampling.seedFor(jobId, batch * 100);
				assertTrue(seed >= 1 && seed <= Integer.MAX_VALUE);
				if (!seeds.add(seed)) {
					collisions++;
				}
			}
		}
		assertTrue(collisions <= 2, "collisions=" + collisions);
		assertEquals(MpnnSampling.seedFor(17L, 0), MpnnSampling.seedFor(17L, 0));
		assertNotEquals(MpnnSampling.seedFor(17L, 0), MpnnSampling.seedFor(17L, 100));
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
}
