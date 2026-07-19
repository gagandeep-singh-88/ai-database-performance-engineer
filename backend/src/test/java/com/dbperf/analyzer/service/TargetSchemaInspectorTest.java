package com.dbperf.analyzer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSchemaInspectorTest {

    @Test
    void extractsTablesFromJoinsAndSubqueries() {
        String sql = """
                SELECT c.full_name, count(*)
                FROM orders o
                JOIN customers c ON c.id = o.customer_id
                WHERE o.status = 'pending'
                  AND o.id IN (SELECT order_id FROM order_items WHERE quantity > 2)
                """;

        assertThat(TargetSchemaInspector.extractTableNames(sql))
                .containsExactly("orders", "customers", "order_items");
    }

    @Test
    void stripsSchemaQualifiersAndIgnoresKeywords() {
        String sql = "SELECT * FROM public.products p JOIN LATERAL unnest(p.tags) t ON true";

        assertThat(TargetSchemaInspector.extractTableNames(sql))
                .contains("products")
                .doesNotContain("lateral", "unnest");
    }

    @Test
    void handlesNullAndNonSelectStatements() {
        assertThat(TargetSchemaInspector.extractTableNames(null)).isEmpty();
        assertThat(TargetSchemaInspector.extractTableNames("UPDATE orders SET status = 'x'"))
                .containsExactly("orders");
    }
}
