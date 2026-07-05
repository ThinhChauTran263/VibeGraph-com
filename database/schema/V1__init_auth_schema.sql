-- =====================================================================
-- VibeGraph — Postgres schema V1 (control plane: auth / ownership)
-- =====================================================================
-- Đây là "nguồn sự thật" cho USER / QUYỀN SỞ HỮU / QUOTA / API KEY.
-- Đồ thị code vẫn nằm ở Neo4j; Postgres chỉ giữ tầng điều khiển (control plane).
-- File này cũng chính là Flyway migration: sẽ được copy sang
--   src/main/resources/db/migration/V1__init_auth_schema.sql
-- khi cài Flyway vào backend. Giữ 1 bản duy nhất ở đây để không lệch.
-- =====================================================================

-- gen_random_uuid() nằm trong extension pgcrypto (Postgres 13+).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Trigger dùng chung: tự cập nhật updated_at mỗi lần UPDATE.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- users — tài khoản đăng nhập
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,          -- BCrypt hash, KHÔNG lưu mật khẩu thô
    display_name  VARCHAR(120),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',       -- USER | ADMIN
    quota_bytes   BIGINT       NOT NULL DEFAULT 524288000,    -- hạn mức lưu (mặc định 500MB)
    used_bytes    BIGINT       NOT NULL DEFAULT 0,            -- đã dùng
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role  CHECK (role IN ('USER','ADMIN')),
    CONSTRAINT chk_users_used  CHECK (used_bytes >= 0)
);

-- Email không phân biệt hoa/thường: unique theo lower(email).
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- projects — bản ghi quyền sở hữu (project_id trùng id dùng trong Neo4j)
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    project_id  VARCHAR(64)  PRIMARY KEY,          -- CÙNG id với node :Project trong Neo4j
    owner_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    source_type VARCHAR(20)  NOT NULL,             -- LOCAL | ARCHIVE | GITHUB
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ANALYZING',   -- ANALYZING | ANALYZED | FAILED
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
-- api_keys — khoá cho CLI / MCP client (lưu HASH, không lưu key thô)
-- ---------------------------------------------------------------------
CREATE TABLE api_keys (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash     VARCHAR(255) NOT NULL,            -- hash (SHA-256/BCrypt) của API key
    prefix       VARCHAR(16)  NOT NULL,            -- vd 'vg_live_ab12' để hiển thị nhận biết
    name         VARCHAR(120),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,                       -- != null nghĩa là đã thu hồi
    CONSTRAINT uq_api_keys_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_keys_user ON api_keys (user_id);
