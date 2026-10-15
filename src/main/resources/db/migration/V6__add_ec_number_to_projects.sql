-- EC metadata for the project target. All nullable, no default:
-- NULL means the target is not an enzyme or the EC could not be resolved.

ALTER TABLE projects ADD COLUMN target_pdb_id VARCHAR(16) NULL;
ALTER TABLE projects ADD COLUMN ec_number     VARCHAR(16) NULL;
ALTER TABLE projects ADD COLUMN ec_lineage    VARCHAR(255) NULL;
