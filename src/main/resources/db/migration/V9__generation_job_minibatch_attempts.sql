CREATE TABLE generation_job_minibatch_attempts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  minibatch_id BIGINT NOT NULL,
  attempt INT NOT NULL,
  core_instance_id BIGINT NULL,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  outcome VARCHAR(32) NULL,
  error TEXT NULL,
  CONSTRAINT fk_minibatch_attempt_minibatch FOREIGN KEY (minibatch_id)
    REFERENCES generation_job_minibatches(id) ON DELETE CASCADE,
  CONSTRAINT fk_minibatch_attempt_instance FOREIGN KEY (core_instance_id)
    REFERENCES core_instances(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_minibatch_attempt_minibatch
  ON generation_job_minibatch_attempts (minibatch_id, attempt);
