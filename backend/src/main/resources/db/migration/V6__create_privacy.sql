-- Module: Privacy & Sanitization Engine
-- Per-user privacy controls and an audit trail proving PII never reached the AI.

CREATE TABLE privacy_settings (
    id                       UUID        PRIMARY KEY,
    user_id                  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sql_sanitization_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_enabled               BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_privacy_settings_user UNIQUE (user_id)
);

-- Audit records store only PII TYPES and counts — never raw sensitive values.
CREATE TABLE sanitization_audit (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tenant             VARCHAR(255) NOT NULL,
    analysis_id        UUID,
    pii_detected       TEXT,
    fields_removed     INT         NOT NULL DEFAULT 0,
    payload_size_bytes BIGINT      NOT NULL DEFAULT 0,
    validation_result  VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sanitization_audit_user_time ON sanitization_audit (user_id, created_at DESC);
