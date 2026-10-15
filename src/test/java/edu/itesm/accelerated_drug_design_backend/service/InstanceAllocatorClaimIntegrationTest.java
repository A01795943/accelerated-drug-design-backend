package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class InstanceAllocatorClaimIntegrationTest {

	@Autowired
	private InstanceAllocator allocator;

	@Autowired
	private CoreInstanceRepository coreInstanceRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void tryClaim_secondAttemptOnSameInstanceLoses() {
		Long id = persistFreeInstance("http://10.0.0.21:8000");
		try {
			assertTrue(allocator.tryClaim(id, "run-a", CoreInstance.RUN_KIND_BACKBONE));
			assertFalse(allocator.tryClaim(id, "run-b", CoreInstance.RUN_KIND_GENERATION_JOB));
			assertFalse(allocator.tryClaim(id, "run-a", CoreInstance.RUN_KIND_BACKBONE));

			CoreInstance claimed = coreInstanceRepository.findById(id).orElseThrow();
			assertEquals("run-a", claimed.getCurrentRunId());
			assertEquals(CoreInstance.RUN_KIND_BACKBONE, claimed.getCurrentRunKind());

			allocator.release(id, "wrong-run");
			assertEquals("run-a", coreInstanceRepository.findById(id).orElseThrow().getCurrentRunId());

			allocator.release(id, "run-a");
			CoreInstance freed = coreInstanceRepository.findById(id).orElseThrow();
			assertNull(freed.getCurrentRunId());
			assertNull(freed.getCurrentRunKind());
			assertNull(freed.getClaimedAt());

			assertTrue(allocator.tryClaim(id, "run-b", CoreInstance.RUN_KIND_GENERATION_JOB));
		} finally {
			deleteInstance(id);
		}
	}

	@Test
	void tryClaim_concurrentClaimsOnlyOneWins() throws Exception {
		Long id = persistFreeInstance("http://10.0.0.22:8000");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger wins = new AtomicInteger();
		try {
			List<Future<?>> futures = new ArrayList<>();
			futures.add(pool.submit(() -> claimAfter(start, id, "run-1", wins)));
			futures.add(pool.submit(() -> claimAfter(start, id, "run-2", wins)));
			start.countDown();
			for (Future<?> future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
			assertEquals(1, wins.get());
			CoreInstance claimed = coreInstanceRepository.findById(id).orElseThrow();
			assertTrue("run-1".equals(claimed.getCurrentRunId()) || "run-2".equals(claimed.getCurrentRunId()));
		} finally {
			pool.shutdownNow();
			deleteInstance(id);
		}
	}

	private void claimAfter(CountDownLatch start, Long id, String runId, AtomicInteger wins) {
		try {
			start.await(5, TimeUnit.SECONDS);
			if (allocator.tryClaim(id, runId, CoreInstance.RUN_KIND_BACKBONE)) {
				wins.incrementAndGet();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Long persistFreeInstance(String baseUrl) {
		return transactionTemplate.execute(status -> {
			CoreInstance instance = new CoreInstance();
			instance.setName("cas-" + UUID.randomUUID());
			instance.setBaseUrl(baseUrl);
			instance.setEnabled(true);
			instance.setRestrictSchedule(false);
			instance.setHealthState(CoreInstance.HEALTH_UNKNOWN);
			return coreInstanceRepository.save(instance).getId();
		});
	}

	private void deleteInstance(Long id) {
		transactionTemplate.executeWithoutResult(status -> {
			allocator.release(id, "run-a");
			allocator.release(id, "run-b");
			allocator.release(id, "run-1");
			allocator.release(id, "run-2");
			coreInstanceRepository.findById(id).ifPresent(coreInstanceRepository::delete);
		});
	}
}
