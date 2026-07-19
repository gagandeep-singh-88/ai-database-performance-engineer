-- Module 2: Monitored PostgreSQL connection targets
CREATE TABLE database_connections (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    host           VARCHAR(255) NOT NULL,
    port           INT          NOT NULL DEFAULT 5432,
    database_name  VARCHAR(120) NOT NULL,
    username       VARCHAR(120) NOT NULL,
    secret_ref     VARCHAR(2048) NOT NULL,
    ssl_mode       VARCHAR(20)  NOT NULL DEFAULT 'PREFER',
    status         VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    last_tested_at TIMESTAMPTZ,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_connections_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_connections_user ON database_connections (user_id);
