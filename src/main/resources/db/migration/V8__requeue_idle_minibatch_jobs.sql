-- Minibatch jobs were created as RUNNING before any chunk was posted.
-- V4 already exists (multi-instance core); this is the next free version.
-- Return those jobs to QUEUED when no minibatch has started or finished.
-- A later reassignment that puts every minibatch back to PENDING must not undo RUNNING.

UPDATE generation_jobs
SET status = 'QUEUED'
WHERE status = 'RUNNING'
  AND total_minibatches IS NOT NULL
  AND id NOT IN (
    SELECT generation_job_id
    FROM generation_job_minibatches
    WHERE status IN ('RUNNING', 'COMPLETED')
  );
