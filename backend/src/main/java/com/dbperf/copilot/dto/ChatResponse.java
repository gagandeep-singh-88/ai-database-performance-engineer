package com.dbperf.copilot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param groundedAt when the metric snapshot backing this reply was captured;
 *                   null when the reply was not grounded in live metrics
 */
public record ChatResponse(
        UUID sessionId,
        String reply,
        List<String> suggestedFollowUps,
        String model,
        Instant groundedAt,
        Instant createdAt) {
}
