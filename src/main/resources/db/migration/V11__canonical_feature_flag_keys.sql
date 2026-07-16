-- Preserve the most restrictive state when legacy and canonical rows coexist.
UPDATE feature_flags canonical
SET enabled = canonical.enabled AND legacy.enabled,
    updated_at = now()
FROM feature_flags legacy
WHERE (legacy.flag_key, canonical.flag_key) IN (
    ('global.registration', 'registration'),
    ('global.api_keys', 'api_keys.create.global'),
    ('global.cli_push', 'cli.push'),
    ('global.import_archive', 'import.archive'),
    ('global.import_github', 'import.github'),
    ('global.mcp', 'mcp.enabled')
);

DELETE FROM feature_flags legacy
WHERE legacy.flag_key IN (
    'global.registration',
    'global.api_keys',
    'global.cli_push',
    'global.import_archive',
    'global.import_github',
    'global.mcp'
)
AND EXISTS (
    SELECT 1
    FROM feature_flags canonical
    WHERE canonical.flag_key = CASE legacy.flag_key
        WHEN 'global.registration' THEN 'registration'
        WHEN 'global.api_keys' THEN 'api_keys.create.global'
        WHEN 'global.cli_push' THEN 'cli.push'
        WHEN 'global.import_archive' THEN 'import.archive'
        WHEN 'global.import_github' THEN 'import.github'
        WHEN 'global.mcp' THEN 'mcp.enabled'
    END
);

UPDATE feature_flags
SET flag_key = CASE flag_key
        WHEN 'global.registration' THEN 'registration'
        WHEN 'global.api_keys' THEN 'api_keys.create.global'
        WHEN 'global.cli_push' THEN 'cli.push'
        WHEN 'global.import_archive' THEN 'import.archive'
        WHEN 'global.import_github' THEN 'import.github'
        WHEN 'global.mcp' THEN 'mcp.enabled'
    END,
    scope = 'GLOBAL',
    updated_at = now()
WHERE flag_key IN (
    'global.registration',
    'global.api_keys',
    'global.cli_push',
    'global.import_archive',
    'global.import_github',
    'global.mcp'
);
