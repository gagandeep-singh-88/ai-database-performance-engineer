package com.dbperf.privacy.dto;

/**
 * Request to preview how a payload would be sanitized before an AI call.
 * All fields are optional; provide any combination of SQL, execution plan
 * and metrics JSON.
 *
 * @param sql           raw SQL to sanitize
 * @param executionPlan raw EXPLAIN/EXPLAIN ANALYZE output
 * @param metricsJson   raw metrics document (JSON) to allow-list filter
 */
public record PayloadPreviewRequest(String sql, String executionPlan, String metricsJson) {

    public boolean isEmpty() {
        return blank(sql) && blank(executionPlan) && blank(metricsJson);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
