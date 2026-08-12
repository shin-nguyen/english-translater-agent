CREATE TABLE ai_model_configs (
    id               BIGSERIAL PRIMARY KEY,
    label            VARCHAR(100) NOT NULL,
    provider         VARCHAR(30) NOT NULL,
    base_url         VARCHAR(500) NOT NULL,
    api_key          TEXT NOT NULL,
    model_identifier VARCHAR(200) NOT NULL,
    api_version      VARCHAR(30),
    max_tokens       INTEGER NOT NULL DEFAULT 1536,
    timeout_seconds  INTEGER NOT NULL DEFAULT 20,
    enabled          BOOLEAN NOT NULL DEFAULT true,
    is_default       BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- enforce "at most one default" at the DB level, not just in service code
CREATE UNIQUE INDEX uq_ai_model_configs_single_default ON ai_model_configs (is_default) WHERE is_default = true;
CREATE INDEX idx_ai_model_configs_enabled ON ai_model_configs(enabled);
