-- Preserve API-key lifecycle history when a bound project is removed.
ALTER TABLE api_keys
    DROP CONSTRAINT IF EXISTS fk_api_keys_project;

ALTER TABLE api_keys
    ADD CONSTRAINT fk_api_keys_project
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE SET NULL;

-- An administrator lock cannot be bypassed by deleting its bound project.
CREATE OR REPLACE FUNCTION prevent_locked_api_key_project_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM api_keys
        WHERE project_id = OLD.project_id
          AND deleted_at IS NULL
          AND disabled_by = 'ADMIN'
    ) THEN
        RAISE EXCEPTION 'Cannot delete project with an administrator-locked API key';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_locked_api_key_project_delete ON projects;
CREATE TRIGGER trg_prevent_locked_api_key_project_delete
BEFORE DELETE ON projects
FOR EACH ROW EXECUTE FUNCTION prevent_locked_api_key_project_delete();

-- Safe administrator identity metadata for lock audit/display.
ALTER TABLE api_keys
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);