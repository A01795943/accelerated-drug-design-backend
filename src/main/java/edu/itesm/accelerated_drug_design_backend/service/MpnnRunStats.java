package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobMinibatch;

import java.util.Map;

/** Copies the core's run_stats fields onto a minibatch. Missing keys are left alone. */
public final class MpnnRunStats {

	private MpnnRunStats() {
	}

	public static void apply(GenerationJobMinibatch minibatch, Map<String, Object> details) {
		if (minibatch == null || details == null) {
			return;
		}
		if (details.containsKey("attempts_used")) {
			minibatch.setAttemptsUsed(asInt(details.get("attempts_used")));
		}
		if (details.containsKey("accepted")) {
			minibatch.setAcceptedCount(asInt(details.get("accepted")));
		}
		if (details.containsKey("skipped_db_duplicates")) {
			minibatch.setSkippedDbDuplicates(asInt(details.get("skipped_db_duplicates")));
		}
		if (details.containsKey("skipped_batch_duplicates")) {
			minibatch.setSkippedBatchDuplicates(asInt(details.get("skipped_batch_duplicates")));
		}
		if (details.containsKey("capped")) {
			minibatch.setCapped(asBoolean(details.get("capped")));
		}
	}

	private static Integer asInt(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static boolean asBoolean(Object value) {
		if (value instanceof Boolean flag) {
			return flag;
		}
		if (value instanceof Number number) {
			return number.intValue() != 0;
		}
		return value != null && Boolean.parseBoolean(String.valueOf(value));
	}
}
