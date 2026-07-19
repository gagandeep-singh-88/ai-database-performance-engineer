-- Module 4: AI query analyses (kept for history + the Module 7 report)
CREATE TABLE query_analyses (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    connection_id UUID         REFERENCES database_connections (id) ON DELETE SET NULL,
    sql_text      TEXT,
    plan_text     TEXT,
    summary       TEXT,
    result_json   TEXT         NOT NULL,
    model         VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_analyses_user_time ON query_analyses (user_id, created_at DESC);
