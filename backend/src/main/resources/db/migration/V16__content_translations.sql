CREATE TABLE IF NOT EXISTS content_translations (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    translated_text TEXT,
    source_hash VARCHAR(64),
    origin VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(30),
    error_message TEXT,
    attempts INT NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by UUID,
    CONSTRAINT uq_content_translations_entity UNIQUE (entity_type, entity_id, field_name, locale)
);

CREATE INDEX IF NOT EXISTS idx_content_translations_status ON content_translations (status);
