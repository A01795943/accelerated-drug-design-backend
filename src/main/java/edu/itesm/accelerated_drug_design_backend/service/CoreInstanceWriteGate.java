package edu.itesm.accelerated_drug_design_backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializes health and metrics writes for one core_instances row inside this process
 * so the two schedulers do not update that row at the same time.
 */
@Component
public class CoreInstanceWriteGate {

	private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

	public void withLock(Long instanceId, Runnable action) {
		Object lock = locks.computeIfAbsent(instanceId, id -> new Object());
		synchronized (lock) {
			action.run();
		}
	}
}
