-- Pool of core instances + link runs (backbones, generation_jobs) to the instance that owns them.
-- Seed URL comes from Flyway placeholder coreBaseUrl (spring.flyway.placeholders.coreBaseUrl → app.core.base-url).

CREATE TABLE core_instances (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  base_url VARCHAR(255) NOT NULL UNIQUE,
  enabled TINYINT NOT NULL DEFAULT 1,
  restrict_schedule TINYINT NOT NULL DEFAULT 0,
  max_job_minutes INT NULL,
  connect_timeout_ms INT NULL,
  read_timeout_ms INT NULL,
  current_run_id VARCHAR(64) NULL,
  current_run_kind VARCHAR(16) NULL,
  claimed_at TIMESTAMP NULL,
  health_state VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN',
  last_health_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE TABLE core_instance_windows (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  core_instance_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  CONSTRAINT fk_window_instance FOREIGN KEY (core_instance_id)
    REFERENCES core_instances(id) ON DELETE CASCADE,
  CONSTRAINT chk_dow CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

ALTER TABLE backbones ADD COLUMN core_instance_id BIGINT NULL;
ALTER TABLE generation_jobs ADD COLUMN core_instance_id BIGINT NULL;
ALTER TABLE backbones ADD CONSTRAINT fk_backbone_instance
    FOREIGN KEY (core_instance_id) REFERENCES core_instances(id);
ALTER TABLE generation_jobs ADD CONSTRAINT fk_job_instance
    FOREIGN KEY (core_instance_id) REFERENCES core_instances(id);

INSERT INTO core_instances (name, base_url, enabled, restrict_schedule, health_state)
VALUES ('core-principal', '${coreBaseUrl}', 1, 0, 'UNKNOWN');

UPDATE backbones SET core_instance_id = (SELECT id FROM core_instances WHERE base_url='${coreBaseUrl}') WHERE core_instance_id IS NULL;
UPDATE generation_jobs SET core_instance_id = (SELECT id FROM core_instances WHERE base_url='${coreBaseUrl}') WHERE core_instance_id IS NULL;
