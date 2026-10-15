package edu.itesm.accelerated_drug_design_backend.service;

import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

/** Full stack on the first occurrence of a key; one summary line every N repeats. */
public final class RepeatedLog {

	private final ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
	private final int every;

	public RepeatedLog(int every) {
		this.every = Math.max(1, every);
	}

	public void warn(Logger log, String key, String message, Throwable error) {
		int count = counts.merge(key, 1, Integer::sum);
		if (count == 1) {
			log.warn(message, error);
			return;
		}
		if (count % every == 0) {
			log.warn("{} ({} times)", message, count);
		}
	}

	public void clear(String key) {
		counts.remove(key);
	}
}
