package com.dbperf.analyzer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPromptBuilderTest {

    private final AnalysisPromptBuilder builder = new AnalysisPromptBuilder();

    @Test
    void userPromptIncludesAllProvidedSections() {
        String prompt = builder.userPrompt(
                "SELECT * FROM orders WHERE customer_id = 42",
                "Seq Scan on orders (cost=0.00..2041.00)",
                "- orders: ~100,000 rows");

        assertThat(prompt)
                .contains("## SQL query")
                .contains("SELECT * FROM orders")
                .contains("## Execution plan")
                .contains("Seq Scan on orders")
                .contains("## Schema & statistics context")
                .contains("~100,000 rows");
    }

    @Test
    void userPromptOmitsMissingSections() {
        String prompt = builder.userPrompt(null, "Seq Scan on orders", null);

        assertThat(prompt)
                .doesNotContain("## SQL query")
                .doesNotContain("## Schema")
                .contains("## Execution plan");
    }

    @Test
    void systemPromptDefinesThePersonaAndSeverityScale() {
        assertThat(builder.systemPrompt())
                .contains("PostgreSQL")
                .contains("CREATE INDEX CONCURRENTLY")
                .contains("HIGH");
    }
}
