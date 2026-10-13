-- VRAM megabytes for new samples. Older rows stay NULL and the UI shows them as a gap.
ALTER TABLE core_instance_metrics_history ADD COLUMN IF NOT EXISTS gpu_mem_used_mb DOUBLE NULL;
ALTER TABLE core_instance_metrics_history ADD COLUMN IF NOT EXISTS gpu_mem_total_mb DOUBLE NULL;
