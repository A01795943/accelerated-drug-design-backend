-- Data generation campaigns (serialized backbone + temperature sweep pipeline).

CREATE TABLE data_generation_campaigns (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  num_seqs_per_temperature INT,
  temperatures_json TEXT,
  error TEXT,
  CONSTRAINT fk_campaigns_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_campaigns_project_id ON data_generation_campaigns (project_id);
CREATE INDEX idx_campaigns_status ON data_generation_campaigns (status);

CREATE TABLE campaign_backbones (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  sort_order INT NOT NULL,
  contigs TEXT,
  hotspots TEXT,
  chains_to_remove TEXT,
  iterations INT,
  status VARCHAR(32) NOT NULL,
  backbone_id BIGINT,
  run_id VARCHAR(64),
  error TEXT,
  CONSTRAINT fk_campaign_backbones_campaign
    FOREIGN KEY (campaign_id) REFERENCES data_generation_campaigns (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE INDEX idx_campaign_backbones_campaign_id ON campaign_backbones (campaign_id);

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
