package com.dbperf.analyzer.dto;

import java.util.UUID;

/**
 * @param connectionId  optional — enables auto-EXPLAIN and schema grounding
 * @param sql           SQL to analyze (this and/or explainOutput required)
 * @param explainOutput pasted EXPLAIN/EXPLAIN ANALYZE output
 * @param runAnalyze    when a connection is given: execute EXPLAIN ANALYZE
 *                      (runs the query, read-only) instead of plain EXPLAIN
 */
public record AnalyzeRequest(UUID connectionId, String sql, String explainOutput, boolean runAnalyze) {

    public boolean hasSql() {
        return sql != null && !sql.isBlank();
    }

    public boolean hasExplainOutput() {
        return explainOutput != null && !explainOutput.isBlank();
    }
}
