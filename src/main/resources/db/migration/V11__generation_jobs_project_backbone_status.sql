-- Speeds up the generation-job list filter (project + backbone) and the
-- COMPLETED-id lookup used when consolidating every filtered job.
-- Single-column indexes on project_id and backbone_id already exist; this
-- composite covers the new WHERE project_id = ? AND backbone_id IN (?) AND status = ?.
CREATE INDEX idx_generation_jobs_project_backbone_status
  ON generation_jobs (project_id, backbone_id, status);
