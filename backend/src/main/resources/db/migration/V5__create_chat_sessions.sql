-- Module 6: AI Copilot chat sessions and messages
CREATE TABLE chat_sessions (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    connection_id UUID         REFERENCES database_connections (id) ON DELETE SET NULL,
    title         VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chat_messages (
    id          UUID        PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role        VARCHAR(12) NOT NULL,
    content     TEXT        NOT NULL,
    follow_ups  TEXT,
    model       VARCHAR(60),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_sessions_user_time ON chat_sessions (user_id, updated_at DESC);
CREATE INDEX idx_chat_messages_session_time ON chat_messages (session_id, created_at);
