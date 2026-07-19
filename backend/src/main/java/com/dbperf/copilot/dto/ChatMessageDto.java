package com.dbperf.copilot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        String role,
        String content,
        List<String> suggestedFollowUps,
        String model,
        Instant createdAt) {
}
