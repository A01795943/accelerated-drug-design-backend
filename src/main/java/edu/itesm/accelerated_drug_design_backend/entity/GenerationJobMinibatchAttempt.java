package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "generation_job_minibatch_attempts")
public class GenerationJobMinibatchAttempt {

	public static final String OUTCOME_FAILED = "FAILED";
	public static final String OUTCOME_COMPLETED = "COMPLETED";
	public static final String OUTCOME_REQUEUED = "REQUEUED";
	public static final String OUTCOME_ERROR = "ERROR";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "minibatch_id", nullable = false)
	private Long minibatchId;

	@Column(name = "attempt", nullable = false)
	private int attempt;

	@Column(name = "core_instance_id")
	private Long coreInstanceId;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "outcome", length = 32)
	private String outcome;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getMinibatchId() {
		return minibatchId;
	}

	public void setMinibatchId(Long minibatchId) {
		this.minibatchId = minibatchId;
	}

	public int getAttempt() {
		return attempt;
	}

	public void setAttempt(int attempt) {
		this.attempt = attempt;
	}

	public Long getCoreInstanceId() {
		return coreInstanceId;
	}

	public void setCoreInstanceId(Long coreInstanceId) {
		this.coreInstanceId = coreInstanceId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public String getOutcome() {
		return outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}
}
