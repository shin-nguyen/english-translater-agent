CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contexts (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notes (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    original_text     TEXT NOT NULL,
    detected_language VARCHAR(10) NOT NULL,
    english_result    TEXT NOT NULL,
    alternatives      JSONB NOT NULL DEFAULT '[]'::jsonb,
    analysis          JSONB NOT NULL DEFAULT '[]'::jsonb,
    role_id           BIGINT REFERENCES roles(id) ON DELETE SET NULL,
    context_id        BIGINT REFERENCES contexts(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notes_role_id ON notes(role_id);
CREATE INDEX idx_notes_context_id ON notes(context_id);
CREATE INDEX idx_notes_created_at ON notes(created_at DESC);
