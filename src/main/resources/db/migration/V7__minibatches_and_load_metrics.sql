-- Minibatches and load metrics.
-- Deviation from the planned V3 script: V3-V6 already exist, and V4 already created
-- core_instances (name is not unique; base_url is) plus backbones.core_instance_id.
-- health_state (UP/DOWN/UNKNOWN) stays; load control uses the new status column.

ALTER TABLE core_instances ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE core_instances ADD COLUMN max_concurrent_runs INT NOT NULL DEFAULT 1;
ALTER TABLE core_instances ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
ALTER TABLE core_instances ADD COLUMN last_metrics_at TIMESTAMP NULL;
ALTER TABLE core_instances ADD COLUMN last_metrics_json TEXT NULL;
ALTER TABLE core_instances ADD COLUMN status_reason VARCHAR(255) NULL;

CREATE TABLE core_instance_metrics_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  core_instance_id BIGINT NOT NULL,
  collected_at TIMESTAMP NOT NULL,
  cpu_percent DOUBLE NULL,
  mem_percent DOUBLE NULL,
  gpu_util_percent DOUBLE NULL,
  gpu_mem_percent DOUBLE NULL,
  disk_percent DOUBLE NULL,
  running_count INT NULL,
  net_sent_per_sec DOUBLE NULL,
  net_recv_per_sec DOUBLE NULL,
  CONSTRAINT fk_metrics_history_instance FOREIGN KEY (core_instance_id)
    REFERENCES core_instances(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_metrics_history_instance_collected
  ON core_instance_metrics_history (core_instance_id, collected_at);

CREATE TABLE generation_job_minibatches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  generation_job_id BIGINT NOT NULL,
  idx INT NOT NULL,
  n_offset INT NOT NULL,
  num_seqs INT NOT NULL,
  run_id VARCHAR(80) NOT NULL,
  core_instance_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  error TEXT NULL,
  fasta LONGTEXT NULL,
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  CONSTRAINT uq_minibatch_run_id UNIQUE (run_id),
  CONSTRAINT uq_minibatch_job_idx UNIQUE (generation_job_id, idx),
  CONSTRAINT fk_minibatch_job FOREIGN KEY (generation_job_id)
    REFERENCES generation_jobs(id) ON DELETE CASCADE,
  CONSTRAINT fk_minibatch_instance FOREIGN KEY (core_instance_id)
    REFERENCES core_instances(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

ALTER TABLE generation_jobs ADD COLUMN total_minibatches INT NULL;
ALTER TABLE generation_jobs ADD COLUMN completed_minibatches INT NULL;
