-- Add deactivated column to users table
ALTER TABLE users ADD COLUMN deactivated BOOLEAN NOT NULL DEFAULT false;
