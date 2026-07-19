package com.dbperf.copilot.dto;

import com.dbperf.copilot.domain.ChatSession;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionSummary(
        UUID id,
        UUID connectionId,
        String title,
        Instant createdAt,
        Instant updatedAt) {

    public static ChatSessionSummary from(ChatSession session) {
        return new ChatSessionSummary(session.getId(), session.getConnectionId(),
                session.getTitle(), session.getCreatedAt(), session.getUpdatedAt());
    }
}
