-- Attempt cap, binder keys, and per-minibatch sampling stats.
-- Historical binder keys are filled in Java (H2 has no reliable SUBSTRING_INDEX).

CREATE TABLE IF NOT EXISTS app_settings (
  setting_key VARCHAR(100) NOT NULL PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL,
  description VARCHAR(500) NULL,
  updated_at TIMESTAMP NULL,
  updated_by VARCHAR(100) NULL
);

INSERT INTO app_settings (setting_key, setting_value, description, updated_at, updated_by)
SELECT 'generation.max_attempt_factor', '20', 'ProteinMPNN attempts per requested sequence', CURRENT_TIMESTAMP, 'flyway'
WHERE NOT EXISTS (
  SELECT 1 FROM app_settings WHERE setting_key = 'generation.max_attempt_factor'
);

CREATE TABLE IF NOT EXISTS backbone_binder_keys (
  backbone_id BIGINT NOT NULL,
  binder_key VARCHAR(64) NOT NULL,
  minibatch_id BIGINT NULL,
  PRIMARY KEY (backbone_id, binder_key),
  CONSTRAINT fk_backbone_binder_keys_backbone FOREIGN KEY (backbone_id)
    REFERENCES backbones(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_backbone_binder_keys_minibatch
  ON backbone_binder_keys (minibatch_id);

ALTER TABLE generation_job_minibatches ADD COLUMN IF NOT EXISTS attempts_used INT NULL;
ALTER TABLE generation_job_minibatches ADD COLUMN IF NOT EXISTS accepted_count INT NULL;
ALTER TABLE generation_job_minibatches ADD COLUMN IF NOT EXISTS skipped_db_duplicates INT NULL;
ALTER TABLE generation_job_minibatches ADD COLUMN IF NOT EXISTS skipped_batch_duplicates INT NULL;
ALTER TABLE generation_job_minibatches ADD COLUMN IF NOT EXISTS capped TINYINT(1) NOT NULL DEFAULT 0;
