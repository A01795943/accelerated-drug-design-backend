package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbLockRetryTest {

	@Test
	void retriesDeadlockThenReturns() {
		AtomicInteger calls = new AtomicInteger();
		int value = DbLockRetry.call("mark job running", () -> {
			if (calls.incrementAndGet() < 3) {
				throw new CannotAcquireLockException("Deadlock found");
			}
			return 7;
		});
		assertEquals(7, value);
		assertEquals(3, calls.get());
	}

	@Test
	void givesUpAfterThreeLockFailures() {
		AtomicInteger calls = new AtomicInteger();
		assertThrows(CannotAcquireLockException.class, () -> DbLockRetry.run("persist health", () -> {
			calls.incrementAndGet();
			throw new CannotAcquireLockException("Lock wait timeout exceeded");
		}));
		assertEquals(3, calls.get());
		assertTrue(DbLockRetry.isLockFailure(new CannotAcquireLockException("Deadlock found")));
	}
}
