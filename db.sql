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