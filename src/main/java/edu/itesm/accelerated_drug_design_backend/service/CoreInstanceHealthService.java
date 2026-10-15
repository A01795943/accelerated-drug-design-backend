package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.core.CoreSystemInterface;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Probes enabled core instances and persists health_state / last_health_at.
 * HTTP stays outside the persistence transaction.
 */
@Service
public class CoreInstanceHealthService {

	private static final Logger log = LoggerFactory.getLogger(CoreInstanceHealthService.class);

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreSystemInterface coreSystem;
	private final CoreInstanceWriteGate writeGate;
	private final TransactionTemplate transactionTemplate;
	private final RepeatedLog repeatedLog = new RepeatedLog(10);

	public CoreInstanceHealthService(
			CoreInstanceRepository coreInstanceRepository,
			CoreSystemInterface coreSystem,
			CoreInstanceWriteGate writeGate,
			PlatformTransactionManager transactionManager) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.coreSystem = coreSystem;
		this.writeGate = writeGate;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		this.transactionTemplate.setTimeout(5);
	}

	public void probeEnabledInstances() {
		List<CoreInstance> enabled = coreInstanceRepository.findByEnabledTrue();
		for (CoreInstance instance : enabled) {
			String state;
			try {
				state = coreSystem.healthCheck(instance);
			} catch (Exception e) {
				repeatedLog.warn(log, "health-" + instance.getId(),
						"Health probe threw for instance " + instance.getId() + ": " + e.getMessage(), e);
				state = CoreInstance.HEALTH_DOWN;
			}
			if (!CoreInstance.HEALTH_UP.equals(state)) {
				state = CoreInstance.HEALTH_DOWN;
			}
			persistHealth(instance.getId(), state);
		}
	}

	public void persistHealth(Long instanceId, String healthState) {
		DbLockRetry.run("persist health " + instanceId, () -> writeGate.withLock(instanceId, () ->
				transactionTemplate.executeWithoutResult(status ->
						coreInstanceRepository.updateHealthState(instanceId, healthState, Instant.now()))));
	}
}
