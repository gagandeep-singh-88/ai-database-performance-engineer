-- Module 3: Historical performance snapshots of monitored targets.
-- Summary columns are queryable for charts; *_detail columns hold JSON
-- for drill-down (kept as TEXT for portability).
CREATE TABLE metric_snapshots (
    id                  UUID        PRIMARY KEY,
    connection_id       UUID        NOT NULL REFERENCES database_connections (id) ON DELETE CASCADE,
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    db_size_bytes       BIGINT      NOT NULL DEFAULT 0,
    active_sessions     INT         NOT NULL DEFAULT 0,
    idle_in_transaction INT         NOT NULL DEFAULT 0,
    blocked_sessions    INT         NOT NULL DEFAULT 0,
    waiting_locks       INT         NOT NULL DEFAULT 0,
    cache_hit_ratio     DOUBLE PRECISION,
    xact_commit         BIGINT      NOT NULL DEFAULT 0,
    xact_rollback       BIGINT      NOT NULL DEFAULT 0,
    deadlocks           BIGINT      NOT NULL DEFAULT 0,
    temp_bytes          BIGINT      NOT NULL DEFAULT 0,
    top_queries         TEXT,
    sessions_detail     TEXT,
    locks_detail        TEXT,
    table_stats         TEXT
);

CREATE INDEX idx_snapshots_conn_time ON metric_snapshots (connection_id, captured_at DESC);
