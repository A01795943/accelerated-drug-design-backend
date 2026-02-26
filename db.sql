-- ============================================================
-- Drug Accelerator - Crear BD y tablas (MySQL)
-- Charset/collation acorde a application.yaml
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
  description TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- ------------------------------------------------------------
-- Tabla: backbones (depende de projects)
-- ------------------------------------------------------------
CREATE TABLE backbones (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  run_id VARCHAR(64),
  status VARCHAR(32),
  error TEXT,
  contigs TEXT,
  hotspots TEXT,
  chains_to_remove TEXT,
  iterations INT,
  structure LONGTEXT,
  CONSTRAINT fk_backbones_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
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
  status VARCHAR(32),
  error TEXT,
  temperature DOUBLE,
  num_seqs INT,
  output_csv LONGTEXT,
  fasta LONGTEXT,
  best_pdb LONGTEXT,
  total_records INT,
  CONSTRAINT fk_generation_jobs_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_generation_jobs_backbone
    FOREIGN KEY (backbone_id) REFERENCES backbones (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_generation_jobs_project_id ON generation_jobs (project_id);
CREATE INDEX idx_generation_jobs_backbone_id ON generation_jobs (backbone_id);
CREATE INDEX idx_generation_jobs_run_id ON generation_jobs (run_id);

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