package com.dbperf.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param sessionId    existing session to continue, or null to start a new one
 * @param connectionId target database to ground the conversation in (optional)
 * @param message      the user's message
 */
public record ChatRequest(
        UUID sessionId,
        UUID connectionId,
        @NotBlank @Size(max = 8000) String message) {
}
