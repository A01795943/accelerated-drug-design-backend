package edu.itesm.accelerated_drug_design_backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds and binder identity shared with {@code pipeline/mpnn_seed.py}.
 * The core adds {@code index * 1_000_003} to this base, so bases that differ
 * by the sequence offset (or by 31 between consecutive job ids) do not overlap.
 */
public final class MpnnSampling {

	private MpnnSampling() {
	}

	/** Base seed for the sequence at {@code nOffset} inside {@code jobId}. */
	public static int seedFor(long jobId, int nOffset) {
		long mixed = jobId * 31L + Math.max(nOffset, 0);
		long reduced = Math.floorMod(mixed, Integer.MAX_VALUE - 1L);
		if (reduced == 0) {
			reduced = 1;
		}
		return (int) reduced;
	}

	/** Binder chain: text after the last '/', without whitespace, uppercased. */
	public static String binderKey(String seq) {
		if (seq == null) {
			return "";
		}
		String text = seq.replaceAll("\\s+", "").toUpperCase();
		if (text.isEmpty()) {
			return "";
		}
		int slash = text.lastIndexOf('/');
		if (slash < 0) {
			return text;
		}
		String tail = text.substring(slash + 1);
		return tail.isEmpty() ? text.replace("/", "") : tail;
	}

	public static List<String> binderKeys(List<String> sequences) {
		Set<String> keys = new LinkedHashSet<>();
		if (sequences == null) {
			return List.of();
		}
		for (String seq : sequences) {
			String key = binderKey(seq);
			if (!key.isEmpty()) {
				keys.add(key);
			}
		}
		return new ArrayList<>(keys);
	}
}
