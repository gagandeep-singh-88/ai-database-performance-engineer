package com.dbperf.analyzer.dto;

import com.dbperf.ai.AiQueryAnalysis;

import java.time.Instant;
import java.util.UUID;

public record QueryAnalysisResponse(
        UUID id,
        UUID connectionId,
        String sql,
        String planUsed,
        String schemaContext,
        AiQueryAnalysis analysis,
        String model,
        Instant createdAt) {
}
