-- =====================================================================
-- VibeGraph - Plans and credit accounting foundation
-- =====================================================================
-- Extends the Phase 4 plan model with monthly credits and creates the
-- credit balance, ledger, and pricing-rule tables. Pricing lives in the
-- database so MCP/CLI credit costs can change without hardcoding values
-- in application business logic.
-- =====================================================================

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS monthly_credit_limit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS contact_sales_required BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_plans_monthly_credit_limit'
    ) THEN
        ALTER TABLE plans
            ADD CONSTRAINT chk_plans_monthly_credit_limit CHECK (monthly_credit_limit >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_plans_sort_order'
    ) THEN
        ALTER TABLE plans
            ADD CONSTRAINT chk_plans_sort_order CHECK (sort_order >= 0);
    END IF;
END;
$$;

INSERT INTO plans (
    code,
    name,
    storage_limit_bytes,
    api_key_limit,
    monthly_credit_limit,
    contact_sales_required,
    is_active,
    sort_order
)
VALUES
    ('FREE', 'Free', 104857600, 3, 100, false, true, 10),
    ('PRO', 'Pro', 524288000, 10, 500, false, true, 20),
    ('PRO_PLUS', 'Pro Plus', 1073741824, 25, 1000, false, true, 30),
    ('MAX', 'Max', 2147483648, 50, 2000, false, true, 40),
    ('ENTERPRISE', 'Enterprise', 0, 0, 0, true, true, 50)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    storage_limit_bytes = EXCLUDED.storage_limit_bytes,
    api_key_limit = EXCLUDED.api_key_limit,
    monthly_credit_limit = EXCLUDED.monthly_credit_limit,
    contact_sales_required = EXCLUDED.contact_sales_required,
    is_active = EXCLUDED.is_active,
    sort_order = EXCLUDED.sort_order;

CREATE TABLE IF NOT EXISTS user_credit_balances (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_start           DATE        NOT NULL,
    period_end             DATE        NOT NULL,
    credits_limit_snapshot INTEGER     NOT NULL,
    credits_used           INTEGER     NOT NULL DEFAULT 0,
    credits_adjustment     INTEGER     NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_credit_balance_period CHECK (period_end > period_start),
    CONSTRAINT chk_credit_balance_limit CHECK (credits_limit_snapshot >= 0),
    CONSTRAINT chk_credit_balance_used CHECK (credits_used >= 0),
    CONSTRAINT uq_credit_balance_user_period UNIQUE (user_id, period_start, period_end)
);

CREATE TRIGGER trg_user_credit_balances_updated
    BEFORE UPDATE ON user_credit_balances
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_user_credit_balances_user
    ON user_credit_balances (user_id);

CREATE INDEX IF NOT EXISTS idx_user_credit_balances_period
    ON user_credit_balances (period_start, period_end);

CREATE TABLE IF NOT EXISTS credit_pricing_rules (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_code       VARCHAR(64)   NOT NULL UNIQUE,
    display_name         VARCHAR(120)  NOT NULL,
    base_credits         NUMERIC(12,4) NOT NULL DEFAULT 0,
    per_file_credits     NUMERIC(12,4) NOT NULL DEFAULT 0,
    per_mb_credits       NUMERIC(12,4) NOT NULL DEFAULT 0,
    per_1k_nodes_credits NUMERIC(12,4) NOT NULL DEFAULT 0,
    minimum_credits      INTEGER       NOT NULL DEFAULT 0,
    is_active            BOOLEAN       NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_credit_pricing_base CHECK (base_credits >= 0),
    CONSTRAINT chk_credit_pricing_file CHECK (per_file_credits >= 0),
    CONSTRAINT chk_credit_pricing_mb CHECK (per_mb_credits >= 0),
    CONSTRAINT chk_credit_pricing_nodes CHECK (per_1k_nodes_credits >= 0),
    CONSTRAINT chk_credit_pricing_min CHECK (minimum_credits >= 0)
);

CREATE TRIGGER trg_credit_pricing_rules_updated
    BEFORE UPDATE ON credit_pricing_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO credit_pricing_rules (
    operation_code,
    display_name,
    base_credits,
    per_file_credits,
    per_mb_credits,
    per_1k_nodes_credits,
    minimum_credits,
    is_active
)
VALUES
    ('MCP_TOOL_CALL', 'MCP tool call', 1, 0, 0, 0, 1, true),
    ('CLI_PUSH', 'CLI push', 1, 0.10, 0, 0, 1, true),
    ('CLI_WATCH_PATCH', 'CLI watch patch', 1, 0.10, 0, 0, 1, true),
    ('PROJECT_ANALYZE', 'Project analyze', 5, 0.01, 1, 1, 5, true),
    ('IMPORT_ARCHIVE', 'Archive import', 3, 0, 1, 0, 3, true),
    ('IMPORT_GITHUB', 'GitHub import', 3, 0, 1, 0, 3, true),
    ('ADMIN_ADJUSTMENT', 'Admin credit adjustment', 0, 0, 0, 0, 0, true)
ON CONFLICT (operation_code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    base_credits = EXCLUDED.base_credits,
    per_file_credits = EXCLUDED.per_file_credits,
    per_mb_credits = EXCLUDED.per_mb_credits,
    per_1k_nodes_credits = EXCLUDED.per_1k_nodes_credits,
    minimum_credits = EXCLUDED.minimum_credits,
    is_active = EXCLUDED.is_active;

CREATE TABLE IF NOT EXISTS credit_ledger (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id           VARCHAR(64) REFERENCES projects(project_id) ON DELETE SET NULL,
    balance_id           UUID        REFERENCES user_credit_balances(id) ON DELETE SET NULL,
    source               VARCHAR(20) NOT NULL,
    operation_code       VARCHAR(64) NOT NULL,
    credits_delta        INTEGER     NOT NULL,
    metadata             JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_credit_ledger_source CHECK (source IN ('MCP','CLI','WEB','ADMIN','SYSTEM')),
    CONSTRAINT chk_credit_ledger_delta CHECK (credits_delta <> 0)
);

CREATE INDEX IF NOT EXISTS idx_credit_ledger_user_created
    ON credit_ledger (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_credit_ledger_operation
    ON credit_ledger (operation_code);

CREATE INDEX IF NOT EXISTS idx_credit_ledger_project
    ON credit_ledger (project_id)
    WHERE project_id IS NOT NULL;
