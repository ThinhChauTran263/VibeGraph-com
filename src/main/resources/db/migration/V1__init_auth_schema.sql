-- =====================================================================
-- VibeGraph — Postgres schema V1 (control plane: auth / ownership)
-- =====================================================================
-- Runtime Flyway migration (source of truth for the running backend).
-- Kept in sync with database/schema/V1__init_auth_schema.sql (reference copy).
-- The code graph stays in Neo4j; Postgres holds only the control plane
-- (users / ownership / quota / API keys).
-- =====================================================================

-- gen_random_uuid() lives in the pgcrypto extension (Postgres 13+).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Shared trigger: refresh updated_at on every UPDATE.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- users — login accounts
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    -- NULLABLE: an OAuth-only user (e.g. Google) has no local password.
    -- A local (email+password) user stores a BCrypt hash here.
    password_hash VARCHAR(255),
    display_name  VARCHAR(120),
    avatar_url    VARCHAR(512),
    email_verified BOOLEAN     NOT NULL DEFAULT false,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    quota_bytes   BIGINT       NOT NULL DEFAULT 524288000,
    used_bytes    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role  CHECK (role IN ('USER','ADMIN')),
    CONSTRAINT chk_users_used  CHECK (used_bytes >= 0)
);

-- Case-insensitive email uniqueness.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- user_identities — external login methods (OAuth: Google now, GitHub later)
-- ---------------------------------------------------------------------
-- Created in Phase 1 (schema frozen) even though Google OAuth wiring is deferred,
-- so re-opening the OAuth card needs no schema change. Local login does NOT use
-- this table (it uses users.password_hash).
CREATE TABLE user_identities (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_identity_provider CHECK (provider IN ('GOOGLE','GITHUB')),
    CONSTRAINT uq_identity_provider_uid UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user ON user_identities (user_id);

-- ---------------------------------------------------------------------
-- projects — ownership record (project_id equals the Neo4j :Project id)
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    project_id  VARCHAR(64)  PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    source_type VARCHAR(20)  NOT NULL,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ANALYZING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_projects_source CHECK (source_type IN ('LOCAL','ARCHIVE','GITHUB')),
    CONSTRAINT chk_projects_status CHECK (status IN ('ANALYZING','ANALYZED','FAILED'))
);

CREATE INDEX idx_projects_owner ON projects (owner_id);

CREATE TRIGGER trg_projects_updated
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- api_keys — CLI / MCP client keys (store the HASH, never the raw key)
-- ---------------------------------------------------------------------
-- Table created now; usage (issue/rotate/revoke) is Phase 3.
CREATE TABLE api_keys (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash     VARCHAR(255) NOT NULL,
    prefix       VARCHAR(16)  NOT NULL,
    name         VARCHAR(120),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    CONSTRAINT uq_api_keys_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_keys_user ON api_keys (user_id);
