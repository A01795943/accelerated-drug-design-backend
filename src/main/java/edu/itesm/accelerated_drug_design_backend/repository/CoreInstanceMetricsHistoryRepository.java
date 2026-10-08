package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceMetricsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CoreInstanceMetricsHistoryRepository extends JpaRepository<CoreInstanceMetricsHistory, Long> {

	List<CoreInstanceMetricsHistory> findByCoreInstanceIdAndCollectedAtBetweenOrderByCollectedAtAsc(
			Long coreInstanceId, Instant from, Instant to);

	@org.springframework.transaction.annotation.Transactional
	long deleteByCollectedAtBefore(Instant cutoff);
}
