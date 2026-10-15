package edu.itesm.accelerated_drug_design_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Bounded retry for InnoDB deadlock (SQLState 40001) and lock-wait timeout.
 * The action must open its own short transaction; retrying inside a transaction
 * that is already marked rollback-only cannot succeed.
 */
public final class DbLockRetry {

	private static final Logger log = LoggerFactory.getLogger(DbLockRetry.class);
	public static final int MAX_ATTEMPTS = 3;

	private DbLockRetry() {
	}

	public static void run(String operation, Runnable action) {
		call(operation, () -> {
			action.run();
			return null;
		});
	}

	public static <T> T call(String operation, Supplier<T> action) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return action.get();
			} catch (RuntimeException ex) {
				if (!isLockFailure(ex) || attempt == MAX_ATTEMPTS) {
					throw ex;
				}
				last = ex;
				log.warn("{} hit a lock conflict ({}/{}): {}", operation, attempt, MAX_ATTEMPTS, rootMessage(ex));
				sleep(50L * attempt);
			}
		}
		throw last;
	}

	public static boolean isLockFailure(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof PessimisticLockingFailureException || current instanceof QueryTimeoutException) {
				return true;
			}
			if (current instanceof SQLException sql) {
				if ("40001".equals(sql.getSQLState()) || "41000".equals(sql.getSQLState())) {
					return true;
				}
				if (sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213) {
					return true;
				}
			}
			String message = current.getMessage();
			if (message != null && (message.contains("Deadlock") || message.contains("Lock wait timeout"))) {
				return true;
			}
		}
		return false;
	}

	private static String rootMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while backing off a lock retry", interrupted);
		}
	}
}
