ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK (role IN ('USER', 'EDITOR', 'ADMIN'));

-- Existing shared notes have no known author and remain writable only by ADMIN.
ALTER TABLE notes ADD COLUMN owner_id BIGINT REFERENCES app_users(id) ON DELETE RESTRICT;
CREATE INDEX notes_owner_idx ON notes(owner_id);
