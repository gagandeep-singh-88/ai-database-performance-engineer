package com.dbperf.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured-output schema for the Module 7 optimization report: an
 * executive narrative plus a prioritized action plan, grounded in the
 * snapshot data supplied in the prompt.
 */
public record AiReportSummary(
        @JsonPropertyDescription("2-3 paragraph executive summary for an engineering lead: overall health, the dominant performance risks, and expected impact of acting on the plan. Reference actual tables, queries and numbers.")
        String executiveSummary,

        @JsonPropertyDescription("4-7 one-sentence key findings, most important first, each referencing concrete evidence from the metrics")
        List<String> keyFindings,

        @JsonPropertyDescription("Prioritized action plan, highest impact first")
        List<ActionItem> actionPlan) {

    public record ActionItem(
            @JsonPropertyDescription("One of: HIGH, MEDIUM, LOW")
            String priority,
            @JsonPropertyDescription("Short imperative title, e.g. 'Add index on orders.customer_id'")
            String title,
            @JsonPropertyDescription("Why this matters and how to do it, referencing snapshot specifics and trade-offs")
            String detail,
            @JsonPropertyDescription("Ready-to-run SQL if directly actionable (CREATE INDEX CONCURRENTLY for indexes); null otherwise")
            String sql,
            @JsonPropertyDescription("Honest estimated impact, e.g. 'p95 of the pending-orders query ~2400ms -> ~100ms'")
            String estimatedImpact) {
    }
}
