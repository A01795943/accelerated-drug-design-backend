-- Queue/dispatch metadata for core runs (QUEUED + retry/backoff).

ALTER TABLE backbones ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE backbones ADD COLUMN next_dispatch_at TIMESTAMP NULL;

ALTER TABLE generation_jobs ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE generation_jobs ADD COLUMN next_dispatch_at TIMESTAMP NULL;
