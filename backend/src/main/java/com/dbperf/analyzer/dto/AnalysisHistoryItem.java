package com.dbperf.analyzer.dto;

import com.dbperf.analyzer.domain.QueryAnalysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisHistoryItem(
        UUID id,
        UUID connectionId,
        String sqlSnippet,
        String summary,
        String model,
        Instant createdAt) {

    public static AnalysisHistoryItem from(QueryAnalysis analysis) {
        String sql = analysis.getSqlText();
        String snippet = sql == null ? "(EXPLAIN output only)"
                : sql.strip().replaceAll("\\s+", " ");
        if (snippet.length() > 140) {
            snippet = snippet.substring(0, 140) + "…";
        }
        return new AnalysisHistoryItem(
                analysis.getId(),
                analysis.getConnectionId(),
                snippet,
                analysis.getSummary(),
                analysis.getModel(),
                analysis.getCreatedAt());
    }
}
