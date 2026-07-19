package com.dbperf.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured-output schema for Claude's query analysis. The Anthropic SDK
 * derives a JSON schema from this record, so the API guarantees responses
 * parse into it — no fence-stripping or lenient JSON handling needed.
 */
public record AiQueryAnalysis(
        @JsonPropertyDescription("2-4 sentence plain-English explanation of what the query does and its main performance problem")
        String summary,

        @JsonPropertyDescription("Performance issues found, ordered most severe first")
        List<Issue> issues,

        @JsonPropertyDescription("Concrete optimization recommendations, ordered by impact")
        List<Recommendation> recommendations,

        @JsonPropertyDescription("Rewritten, optimized version of the SQL; null if the SQL itself is already optimal")
        String optimizedSql,

        @JsonPropertyDescription("Overall estimated improvement if all recommendations are applied, e.g. '10-50x faster (2400ms -> ~50-200ms)'")
        String estimatedImprovement,

        @JsonPropertyDescription("Step-by-step walkthrough of the execution plan in plain English; null if no plan was provided")
        String planExplanation) {

    public record Issue(
            @JsonPropertyDescription("One of: HIGH, MEDIUM, LOW, INFO")
            String severity,
            @JsonPropertyDescription("Short machine-friendly type, e.g. SEQ_SCAN, MISSING_INDEX, INEFFICIENT_JOIN, SORT_SPILL, STALE_STATS, CARDINALITY_MISESTIMATE")
            String type,
            @JsonPropertyDescription("Specific description of the issue, referencing table/column names")
            String description) {
    }

    public record Recommendation(
            @JsonPropertyDescription("Short imperative title, e.g. 'Add index on orders.customer_id'")
            String title,
            @JsonPropertyDescription("Why this helps and any trade-offs")
            String detail,
            @JsonPropertyDescription("Ready-to-run SQL implementing the recommendation (use CREATE INDEX CONCURRENTLY for indexes); null if not applicable")
            String sql,
            @JsonPropertyDescription("Estimated improvement from this recommendation alone, e.g. '~20x on this query'")
            String estimatedImprovement) {
    }
}
