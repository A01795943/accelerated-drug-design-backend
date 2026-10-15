package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CoreInstanceRepository extends JpaRepository<CoreInstance, Long> {

	boolean existsByBaseUrl(String baseUrl);

	boolean existsByBaseUrlAndIdNot(String baseUrl, Long id);

	@EntityGraph(attributePaths = "windows")
	@Query("SELECT DISTINCT i FROM CoreInstance i ORDER BY i.id ASC")
	List<CoreInstance> findAllWithWindows();

	@EntityGraph(attributePaths = "windows")
	@Query("SELECT i FROM CoreInstance i WHERE i.id = :id")
	Optional<CoreInstance> findWithWindowsById(@Param("id") Long id);

	Optional<CoreInstance> findByBaseUrl(String baseUrl);

	Optional<CoreInstance> findTopByOrderByIdAsc();

	List<CoreInstance> findByEnabledTrue();

	List<CoreInstance> findByCurrentRunIdIsNotNull();

	Optional<CoreInstance> findByCurrentRunId(String currentRunId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE CoreInstance i
			SET i.currentRunId = :runId, i.currentRunKind = :runKind, i.claimedAt = :claimedAt
			WHERE i.id = :id AND i.currentRunId IS NULL AND i.enabled = true
			""")
	int claimIfFree(
			@Param("id") Long id,
			@Param("runId") String runId,
			@Param("runKind") String runKind,
			@Param("claimedAt") Instant claimedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE CoreInstance i
			SET i.currentRunId = NULL, i.currentRunKind = NULL, i.claimedAt = NULL
			WHERE i.id = :id AND i.currentRunId = :runId
			""")
	int releaseIfOwner(@Param("id") Long id, @Param("runId") String runId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE CoreInstance i
			SET i.status = :status,
				i.statusReason = :statusReason,
				i.consecutiveFailures = :consecutiveFailures,
				i.lastMetricsAt = :lastMetricsAt,
				i.lastMetricsJson = :lastMetricsJson
			WHERE i.id = :id
			""")
	int updateLoadSnapshot(
			@Param("id") Long id,
			@Param("status") String status,
			@Param("statusReason") String statusReason,
			@Param("consecutiveFailures") int consecutiveFailures,
			@Param("lastMetricsAt") Instant lastMetricsAt,
			@Param("lastMetricsJson") String lastMetricsJson);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE CoreInstance i
			SET i.status = :status,
				i.statusReason = :statusReason,
				i.consecutiveFailures = :consecutiveFailures
			WHERE i.id = :id
			""")
	int updateReachability(
			@Param("id") Long id,
			@Param("status") String status,
			@Param("statusReason") String statusReason,
			@Param("consecutiveFailures") int consecutiveFailures);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE CoreInstance i
			SET i.healthState = :healthState, i.lastHealthAt = :lastHealthAt
			WHERE i.id = :id
			""")
	int updateHealthState(
			@Param("id") Long id,
			@Param("healthState") String healthState,
			@Param("lastHealthAt") Instant lastHealthAt);
}
