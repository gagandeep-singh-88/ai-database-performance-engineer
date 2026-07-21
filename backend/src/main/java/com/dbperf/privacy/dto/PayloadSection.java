package com.dbperf.privacy.dto;

/**
 * The three parts of an AI payload, before or after sanitization. Used to
 * render the side-by-side "Original vs Sanitized" view on the Privacy page.
 */
public record PayloadSection(String sql, String executionPlan, String metrics) {
}
