package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
		name = "generation_job_minibatches",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_minibatch_run_id", columnNames = "run_id"),
				@UniqueConstraint(name = "uq_minibatch_job_idx", columnNames = {"generation_job_id", "idx"})
		}
)
public class GenerationJobMinibatch {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_ERROR = "ERROR";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "generation_job_id", nullable = false)
	private Long generationJobId;

	@Column(name = "idx", nullable = false)
	private int idx;

	@Column(name = "n_offset", nullable = false)
	private int nOffset;

	@Column(name = "num_seqs", nullable = false)
	private int numSeqs;

	@Column(name = "run_id", nullable = false, unique = true, length = 80)
	private String runId;

	@Column(name = "core_instance_id")
	private Long coreInstanceId;

	@Column(name = "status", nullable = false, length = 16)
	private String status;

	@Column(name = "attempts", nullable = false)
	private int attempts = 0;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	@Column(name = "fasta", columnDefinition = "LONGTEXT")
	private String fasta;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getGenerationJobId() {
		return generationJobId;
	}

	public void setGenerationJobId(Long generationJobId) {
		this.generationJobId = generationJobId;
	}

	public int getIdx() {
		return idx;
	}

	public void setIdx(int idx) {
		this.idx = idx;
	}

	public int getNOffset() {
		return nOffset;
	}

	public void setNOffset(int nOffset) {
		this.nOffset = nOffset;
	}

	public int getNumSeqs() {
		return numSeqs;
	}

	public void setNumSeqs(int numSeqs) {
		this.numSeqs = numSeqs;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public Long getCoreInstanceId() {
		return coreInstanceId;
	}

	public void setCoreInstanceId(Long coreInstanceId) {
		this.coreInstanceId = coreInstanceId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getFasta() {
		return fasta;
	}

	public void setFasta(String fasta) {
		this.fasta = fasta;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
