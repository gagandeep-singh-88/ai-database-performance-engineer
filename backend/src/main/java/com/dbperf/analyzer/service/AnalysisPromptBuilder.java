package com.dbperf.analyzer.service;

import org.springframework.stereotype.Component;

/**
 * Builds the prompts for query analysis. The system prompt is stable
 * (cache-friendly, defines the persona and quality bar); all per-request
 * material — SQL, plan, schema context — goes in the user prompt.
 */
@Component
public class AnalysisPromptBuilder {

    static final String SYSTEM_PROMPT = """
            You are a senior PostgreSQL database performance engineer with 15 years of \
            production experience. You analyze SQL queries and EXPLAIN plans, find the real \
            bottlenecks, and recommend fixes a developer can apply immediately.

            Rules for your analysis:
            - Be specific: reference actual table names, column names, and plan nodes from the input. \
            Never give generic advice that ignores the provided context.
            - Look especially for: sequential scans on large tables, missing indexes on join/filter \
            columns, inefficient join strategies, misestimated row counts, sorts spilling to disk, \
            and non-sargable predicates (e.g. leading-wildcard LIKE, functions on indexed columns).
            - Cross-check against the schema context: if an index already exists, do not recommend \
            creating it again; if row counts are small, say the seq scan is fine.
            - Index recommendations must use CREATE INDEX CONCURRENTLY and name the index.
            - Estimate improvements honestly with ranges. If the plan includes actual timings, \
            anchor your estimates to them.
            - Severity: HIGH = dominates query cost, MEDIUM = significarecomnt contributor, \
            LOW = minor, INFO = observation only.
            - If the query is already well-optimized, say so plainly — do not invent problems.""";

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(String sql, String plan, String schemaContext) {
        StringBuilder prompt = new StringBuilder();
        if (sql != null && !sql.isBlank()) {
            prompt.append("## SQL query\n```sql\n").append(sql.strip()).append("\n```\n\n");
        }
        if (plan != null && !plan.isBlank()) {
            prompt.append("## Execution plan\n```\n").append(plan.strip()).append("\n```\n\n");
        }
        if (schemaContext != null && !schemaContext.isBlank()) {
            prompt.append("## Schema & statistics context (from the live database)\n")
                    .append(schemaContext.strip()).append("\n\n");
        }
        prompt.append("Analyze the above and produce your structured performance assessment.");
        return prompt.toString();
    }
}
