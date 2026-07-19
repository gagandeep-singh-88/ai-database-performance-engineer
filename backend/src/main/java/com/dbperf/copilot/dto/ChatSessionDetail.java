package com.dbperf.copilot.dto;

import java.util.List;

public record ChatSessionDetail(
        ChatSessionSummary session,
        List<ChatMessageDto> messages) {
}
