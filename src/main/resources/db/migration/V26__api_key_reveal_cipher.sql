-- Revealable API keys: store an AES-256-GCM encrypted copy of the secret so the
-- owner can reveal it again from the console. Keys created before this
-- migration keep NULL here and remain reveal-unavailable (their secret was
-- shown exactly once at creation time).
ALTER TABLE api_keys ADD COLUMN secret_cipher TEXT;
