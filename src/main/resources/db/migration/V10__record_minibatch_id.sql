-- Equality deletes use minibatch_id (DELETE WHERE minibatch_id = ?).
-- The foreign key to generation_job_minibatches is omitted on purpose:
-- MariaDB rebuilds generation_jobs_records ("Copy to tmp table") to add it,
-- and that table stores PDB text in LONGTEXT.
-- DROP removes the constraint if an interrupted attempt managed to finish.
ALTER TABLE generation_jobs_records
  DROP FOREIGN KEY IF EXISTS fk_generation_jobs_records_minibatch;

ALTER TABLE generation_jobs_records
  ADD COLUMN IF NOT EXISTS minibatch_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_generation_jobs_records_minibatch
  ON generation_jobs_records (minibatch_id);
