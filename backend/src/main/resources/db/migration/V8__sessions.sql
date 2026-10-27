-- Preserve existing token hashes and expiry while introducing session identity.
ALTER TABLE refresh_tokens RENAME TO sessions;
ALTER TABLE sessions RENAME COLUMN token_hash TO last_refresh_token_hash;
ALTER TABLE sessions DROP CONSTRAINT refresh_tokens_pkey;
ALTER TABLE sessions ADD COLUMN session_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
ALTER TABLE sessions ADD COLUMN device VARCHAR(512) NOT NULL DEFAULT 'Unknown';
-- Historical initiation times were not recorded; use the migration time for those rows.
ALTER TABLE sessions ADD COLUMN initiated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sessions ADD COLUMN is_revoked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessions ADD CONSTRAINT sessions_last_refresh_token_unique UNIQUE (last_refresh_token_hash);
ALTER INDEX refresh_tokens_expiry RENAME TO sessions_expiry;
CREATE INDEX sessions_user_id ON sessions(user_id);
