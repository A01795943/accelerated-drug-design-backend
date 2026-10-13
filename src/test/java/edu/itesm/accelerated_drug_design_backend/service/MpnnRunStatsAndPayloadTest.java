package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpnnRunStatsAndPayloadTest {

	@Test
	void oldCoreDetailWithoutNewFieldsLeavesStatsUnset() {
		GenerationJobMinibatch minibatch = new GenerationJobMinibatch();
		MpnnRunStats.apply(minibatch, Map.of("run_name", "legacy", "num_seqs", 8));
		assertNull(minibatch.getAttemptsUsed());
		assertNull(minibatch.getAcceptedCount());
		assertNull(minibatch.getSkippedDbDuplicates());
		assertNull(minibatch.getSkippedBatchDuplicates());
		assertFalse(minibatch.isCapped());
	}

	@Test
	void numericStatsAcceptIntegerOrDouble() {
		GenerationJobMinibatch minibatch = new GenerationJobMinibatch();
		MpnnRunStats.apply(minibatch, Map.of(
				"attempts_used", 12.0,
				"accepted", 4,
				"skipped_db_duplicates", 2,
				"skipped_batch_duplicates", 1,
				"capped", true));
		assertEquals(12, minibatch.getAttemptsUsed());
		assertEquals(4, minibatch.getAcceptedCount());
		assertEquals(2, minibatch.getSkippedDbDuplicates());
		assertEquals(1, minibatch.getSkippedBatchDuplicates());
		assertTrue(minibatch.isCapped());
	}

	@Test
	void payloadSizeAt100kAnd500kKeys() {
		int at100k = jsonBytes(100_000, 2_000);
		int at500k = jsonBytes(500_000, 10_000);
		System.out.println("exclude_binder_seqs payload bytes 100k=" + at100k + " 500k=" + at500k);
		assertTrue(at100k > 1_000_000);
		assertTrue(at500k > at100k);
	}

	private static int jsonBytes(int keys, int maxAttempts) {
		StringBuilder body = new StringBuilder(keys * 18);
		body.append("{\"max_attempts\":").append(maxAttempts).append(",\"exclude_binder_seqs\":[");
		for (int i = 0; i < keys; i++) {
			if (i > 0) {
				body.append(',');
			}
			body.append("\"CCCCCCCCCCCCCC\"");
		}
		body.append("]}");
		return body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}
}
