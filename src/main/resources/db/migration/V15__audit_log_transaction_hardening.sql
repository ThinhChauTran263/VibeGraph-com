-- Audit rows must commit independently even while a user mutation is uncommitted or locked.
-- Actor and target UUIDs remain immutable historical identifiers, not live relationships.
ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_actor_user_id_fkey;

ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_target_user_id_fkey;
