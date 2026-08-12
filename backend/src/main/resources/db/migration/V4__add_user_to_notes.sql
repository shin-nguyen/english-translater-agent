-- This app is pre-release with no real users yet. Existing notes (if any, e.g. seeded/dev
-- data) have no determinable owner, so rather than inventing a synthetic "system" owner we
-- clear them outright. This is a one-time, explicitly-accepted data loss for dev-stage data.
DELETE FROM notes;

ALTER TABLE notes ADD COLUMN user_id BIGINT REFERENCES app_users(id) ON DELETE CASCADE;
ALTER TABLE notes ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX idx_notes_user_id ON notes(user_id);
