ALTER TABLE cli_device_authorizations
    ADD COLUMN IF NOT EXISTS preferred_api_key_id UUID REFERENCES api_keys(id) ON DELETE SET NULL;
