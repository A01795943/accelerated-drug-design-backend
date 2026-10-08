-- ============================================================
-- Drug Accelerator - Snapshot de schema completo (REFERENCIA)
-- ============================================================
-- NOTA: Este archivo ya NO es la fuente de verdad para crear ni migrar el schema.
-- Las migraciones versionadas viven en:
--   src/main/resources/db/migration/
-- gestionadas por Flyway al arrancar la aplicación.
-- Este db.sql se mantiene solo como snapshot legible del schema actual.
-- ============================================================

CREATE DATABASE IF NOT EXISTS drug_accelerator
  CHARACTER SET utf8
  COLLATE utf8_general_ci;

USE drug_accelerator;

-- ------------------------------------------------------------
-- Tabla: projects
-- ------------------------------------------------------------
CREATE TABLE projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  target LONGTEXT,
  complex LONGTEXT,
  description TEXT,
  target_pdb_id VARCHAR(16) NULL,
  ec_number VARCHAR(16) NULL,
  ec_lineage VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- ------------------------------------------------------------
-- Tabla: core_instances (pool de cores; seed en Flyway V4 via placeholder)
-- ------------------------------------------------------------
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
  status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
  max_concurrent_runs INT NOT NULL DEFAULT 1,
  consecutive_failures INT NOT NULL DEFAULT 0,
  last_metrics_at TIMESTAMP NULL,
  last_metrics_json TEXT NULL,
  status_reason VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- ------------------------------------------------------------
-- Tabla: core_instance_windows (ventanas ISO-8601; 1=lunes .. 7=domingo)
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- Tabla: backbones (depende de projects, core_instances)
-- ------------------------------------------------------------
CREATE TABLE backbones (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  run_id VARCHAR(64),
  core_instance_id BIGINT,
  status VARCHAR(32),
  error TEXT,
  contigs TEXT,
  hotspots TEXT,
  chains_to_remove TEXT,
  iterations INT,
  structure LONGTEXT,
  dispatch_attempts INT NOT NULL DEFAULT 0,
  next_dispatch_at TIMESTAMP NULL,
  CONSTRAINT fk_backbones_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_backbone_instance
    FOREIGN KEY (core_instance_id) REFERENCES core_instances (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_backbones_project_id ON backbones (project_id);
CREATE INDEX idx_backbones_run_id ON backbones (run_id);

-- ------------------------------------------------------------
-- Tabla: generation_jobs (depende de projects y backbones)
-- ------------------------------------------------------------
CREATE TABLE generation_jobs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id VARCHAR(64) UNIQUE,
  created_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  project_id BIGINT NOT NULL,
  backbone_id BIGINT NOT NULL,
  core_instance_id BIGINT,
  status VARCHAR(32),
  error TEXT,
  temperature DOUBLE,
  num_seqs INT,
  output_csv LONGTEXT,
  fasta LONGTEXT,
  best_pdb LONGTEXT,
  total_records INT,
  dispatch_attempts INT NOT NULL DEFAULT 0,
  next_dispatch_at TIMESTAMP NULL,
  total_minibatches INT NULL,
  completed_minibatches INT NULL,
  CONSTRAINT fk_generation_jobs_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_generation_jobs_backbone
    FOREIGN KEY (backbone_id) REFERENCES backbones (id) ON DELETE RESTRICT,
  CONSTRAINT fk_job_instance
    FOREIGN KEY (core_instance_id) REFERENCES core_instances (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_generation_jobs_project_id ON generation_jobs (project_id);
CREATE INDEX idx_generation_jobs_backbone_id ON generation_jobs (backbone_id);
CREATE INDEX idx_generation_jobs_run_id ON generation_jobs (run_id);

-- ------------------------------------------------------------
-- Tabla: core_instance_metrics_history (snapshot cada 5 min)
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- Tabla: generation_job_minibatches (chunks de max 100 secuencias)
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- Tabla: generation_jobs_records (depende de generation_jobs)
-- PK compuesta (generation_job_id, n)
-- ------------------------------------------------------------
CREATE TABLE generation_jobs_records (
  generation_job_id BIGINT NOT NULL,
  n INT NOT NULL,
  pdb LONGTEXT,
  mpnn VARCHAR(255),
  plddt DOUBLE,
  ptm DOUBLE,
  i_ptm DOUBLE,
  pae TEXT,
  i_pae TEXT,
  rmsd DOUBLE,
  seq TEXT,
  PRIMARY KEY (generation_job_id, n),
  CONSTRAINT fk_generation_jobs_records_job
    FOREIGN KEY (generation_job_id) REFERENCES generation_jobs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_generation_jobs_records_job_id ON generation_jobs_records (generation_job_id);

-- ------------------------------------------------------------
-- Tabla: data_generation_campaigns (pipeline serializado)
-- ------------------------------------------------------------
CREATE TABLE data_generation_campaigns (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  num_seqs_per_temperature INT,
  temperatures_json TEXT,
  backbone_configs_json TEXT,
  error TEXT,
  CONSTRAINT fk_campaigns_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_campaigns_project_id ON data_generation_campaigns (project_id);
CREATE INDEX idx_campaigns_status ON data_generation_campaigns (status);

-- ------------------------------------------------------------
-- Tabla: campaign_backbones
-- ------------------------------------------------------------
CREATE TABLE campaign_backbones (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  sort_order INT NOT NULL,
  config_index INT NOT NULL DEFAULT 0,
  contigs TEXT,
  hotspots TEXT,
  chains_to_remove TEXT,
  iterations INT,
  status VARCHAR(32) NOT NULL,
  backbone_id BIGINT,
  run_id VARCHAR(64),
  error TEXT,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL,
  CONSTRAINT fk_campaign_backbones_campaign
    FOREIGN KEY (campaign_id) REFERENCES data_generation_campaigns (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_campaign_backbones_campaign_id ON campaign_backbones (campaign_id);

-- ------------------------------------------------------------
-- Tabla: campaign_temperature_jobs
-- ------------------------------------------------------------
CREATE TABLE campaign_temperature_jobs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_backbone_id BIGINT NOT NULL,
  temperature DOUBLE NOT NULL,
  status VARCHAR(32) NOT NULL,
  generation_job_id BIGINT,
  run_id VARCHAR(64),
  total_records INT,
  error TEXT,
  CONSTRAINT fk_campaign_temp_jobs_backbone
    FOREIGN KEY (campaign_backbone_id) REFERENCES campaign_backbones (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_campaign_temp_jobs_backbone_id ON campaign_temperature_jobs (campaign_backbone_id);

-- ------------------------------------------------------------
-- Tablas para Spring Security (usuarios, roles, permisos)
-- ------------------------------------------------------------

-- Tabla: users (usuarios del sistema)
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);

-- Tabla: roles (USER, ADMIN)
CREATE TABLE roles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- Tabla: user_roles (relación usuario-rol)
CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- Tabla: refresh_tokens (opcional, para renovar JWT)
CREATE TABLE refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(512) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Datos iniciales: roles
INSERT INTO roles (name) VALUES ('USER'), ('ADMIN');

-- Usuario admin con contraseña "admin" (BCrypt hash, strength 10)
INSERT INTO users (username, email, password_hash, enabled) VALUES
  ('admin', 'admin@admin.com', '$2a$10$yrDEkHToJqZbLvfZEWui3u/KUxjZVuH57anj7iDrYajMjvqthjN/2', 1);

-- Asignar rol ADMIN al usuario admin (role_id 2 = ADMIN)
INSERT INTO user_roles (user_id, role_id) VALUES (1, 2);
