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
    monthly_credit_limit = EXCLUDED.monthly_credit_limit,
    contact_sales_required = EXCLUDED.contact_sales_required,
    is_active = EXCLUDED.is_active,
    sort_order = EXCLUDED.sort_order;

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
    ('MCP_TOOL_CALL', 'MCP tool call', 1, 0, 0, 0, 0, true),
    ('CLI_PUSH', 'CLI push', 1, 0.10, 0, 0, 0, true),
    ('PROJECT_ANALYZE', 'Project analyze', 5, 0.01, 1, 0, 0, true),
    ('IMPORT_ARCHIVE', 'Archive import', 3, 0, 1, 0, 0, true),
    ('IMPORT_GITHUB', 'GitHub import', 3, 0, 1, 0, 0, true)
ON CONFLICT (operation_code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    base_credits = EXCLUDED.base_credits,
    per_file_credits = EXCLUDED.per_file_credits,
    per_mb_credits = EXCLUDED.per_mb_credits,
    per_1k_nodes_credits = EXCLUDED.per_1k_nodes_credits,
    minimum_credits = EXCLUDED.minimum_credits,
    is_active = EXCLUDED.is_active;
