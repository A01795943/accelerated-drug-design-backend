package edu.itesm.accelerated_drug_design_backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds and binder identity shared with {@code pipeline/mpnn_seed.py}.
 * {@link #seedFor} is a 31-bit mix of the job id and the sequence offset,
 * so a retry of the same minibatch asks for the same base seed.
 */
public final class MpnnSampling {

	private MpnnSampling() {
	}

	/** 31-bit seed in {@code 1 .. Integer.MAX_VALUE}, stable for the same inputs. */
	public static int seedFor(long jobId, int nOffset) {
		long mixed = splitmix64(jobId);
		long offset = Integer.toUnsignedLong(Math.max(nOffset, 0));
		mixed = splitmix64(mixed ^ (offset * 0x9E3779B97F4A7C15L));
		int seed = (int) (mixed & 0x7FFFFFFFL);
		return seed == 0 ? 1 : seed;
	}

	private static long splitmix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static String storedBinderKey(String seq) {
		String key = binderKey(seq);
		if (key.length() <= 64) {
			return key;
		}
		return key.substring(0, 64);
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
