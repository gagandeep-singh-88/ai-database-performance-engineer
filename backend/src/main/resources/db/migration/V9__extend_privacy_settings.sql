-- Module 8: Settings — Tab 3 (AI &amp; Privacy)
ALTER TABLE privacy_settings
    ADD COLUMN payload_validation_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN show_payload_preview       BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN block_on_pii_detected      BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN sanitization_mode          VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC',
    ADD COLUMN ai_response_style          VARCHAR(20) NOT NULL DEFAULT 'TECHNICAL',
    ADD COLUMN max_response_length        INT         NOT NULL DEFAULT 1000;
