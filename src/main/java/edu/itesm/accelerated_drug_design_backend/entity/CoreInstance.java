package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "core_instances")
public class CoreInstance {

	public static final String HEALTH_UP = "UP";
	public static final String HEALTH_DOWN = "DOWN";
	public static final String HEALTH_UNKNOWN = "UNKNOWN";

	public static final String STATUS_AVAILABLE = "AVAILABLE";
	public static final String STATUS_OVERLOADED = "OVERLOADED";
	public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

	public static final String RUN_KIND_BACKBONE = "BACKBONE";
	public static final String RUN_KIND_GENERATION_JOB = "GENERATION_JOB";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "base_url", nullable = false, unique = true, length = 255)
	private String baseUrl;

	@Column(name = "enabled", nullable = false)
	@JdbcTypeCode(SqlTypes.TINYINT)
	private boolean enabled = true;

	@Column(name = "restrict_schedule", nullable = false)
	@JdbcTypeCode(SqlTypes.TINYINT)
	private boolean restrictSchedule = false;

	@Column(name = "max_job_minutes")
	private Integer maxJobMinutes;

	@Column(name = "connect_timeout_ms")
	private Integer connectTimeoutMs;

	@Column(name = "read_timeout_ms")
	private Integer readTimeoutMs;

	@Column(name = "current_run_id", length = 64)
	private String currentRunId;

	@Column(name = "current_run_kind", length = 16)
	private String currentRunKind;

	@Column(name = "claimed_at")
	private Instant claimedAt;

	@Column(name = "health_state", nullable = false, length = 8)
	private String healthState = HEALTH_UNKNOWN;

	@Column(name = "last_health_at")
	private Instant lastHealthAt;

	@Column(name = "status", nullable = false, length = 16)
	private String status = STATUS_AVAILABLE;

	@Column(name = "max_concurrent_runs", nullable = false)
	private int maxConcurrentRuns = 1;

	@Column(name = "consecutive_failures", nullable = false)
	private int consecutiveFailures = 0;

	@Column(name = "last_metrics_at")
	private Instant lastMetricsAt;

	@Column(name = "last_metrics_json", columnDefinition = "TEXT")
	private String lastMetricsJson;

	@Column(name = "status_reason", length = 255)
	private String statusReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "coreInstance", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<CoreInstanceWindow> windows = new ArrayList<>();

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
		if (healthState == null) {
			healthState = HEALTH_UNKNOWN;
		}
		if (status == null) {
			status = STATUS_AVAILABLE;
		}
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isRestrictSchedule() {
		return restrictSchedule;
	}

	public void setRestrictSchedule(boolean restrictSchedule) {
		this.restrictSchedule = restrictSchedule;
	}

	public Integer getMaxJobMinutes() {
		return maxJobMinutes;
	}

	public void setMaxJobMinutes(Integer maxJobMinutes) {
		this.maxJobMinutes = maxJobMinutes;
	}

	public Integer getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(Integer connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public Integer getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(Integer readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}

	public String getCurrentRunId() {
		return currentRunId;
	}

	public void setCurrentRunId(String currentRunId) {
		this.currentRunId = currentRunId;
	}

	public String getCurrentRunKind() {
		return currentRunKind;
	}

	public void setCurrentRunKind(String currentRunKind) {
		this.currentRunKind = currentRunKind;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public void setClaimedAt(Instant claimedAt) {
		this.claimedAt = claimedAt;
	}

	public String getHealthState() {
		return healthState;
	}

	public void setHealthState(String healthState) {
		this.healthState = healthState;
	}

	public Instant getLastHealthAt() {
		return lastHealthAt;
	}

	public void setLastHealthAt(Instant lastHealthAt) {
		this.lastHealthAt = lastHealthAt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getMaxConcurrentRuns() {
		return maxConcurrentRuns;
	}

	public void setMaxConcurrentRuns(int maxConcurrentRuns) {
		this.maxConcurrentRuns = maxConcurrentRuns;
	}

	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	public void setConsecutiveFailures(int consecutiveFailures) {
		this.consecutiveFailures = consecutiveFailures;
	}

	public Instant getLastMetricsAt() {
		return lastMetricsAt;
	}

	public void setLastMetricsAt(Instant lastMetricsAt) {
		this.lastMetricsAt = lastMetricsAt;
	}

	public String getLastMetricsJson() {
		return lastMetricsJson;
	}

	public void setLastMetricsJson(String lastMetricsJson) {
		this.lastMetricsJson = lastMetricsJson;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(String statusReason) {
		this.statusReason = statusReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public List<CoreInstanceWindow> getWindows() {
		return windows;
	}

	public void setWindows(List<CoreInstanceWindow> windows) {
		this.windows = windows;
	}
}
