package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.dto.MetricsSanitizationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsSanitizerTest {

    private final PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", true, true);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MetricsSanitizer sanitizer =
            new MetricsSanitizer(new PiiDetector(properties), mapper);

    @Test
    void keepsPerformanceMetricsAndDropsBusinessData() {
        String json = """
                {
                  "executionTimeMs": 42.5,
                  "rowsExamined": 100000,
                  "cacheHitRatio": 0.98,
                  "bufferReads": 812,
                  "customerEmail": "john@example.com",
                  "customerName": "John Doe",
                  "orderTotal": 249.99
                }""";
        MetricsSanitizationResult result = sanitizer.sanitize(json);

        assertThat(result.json())
                .contains("executionTimeMs")
                .contains("rowsExamined")
                .contains("cacheHitRatio")
                .contains("bufferReads")
                .doesNotContain("customerEmail")
                .doesNotContain("john@example.com")
                .doesNotContain("customerName")
                .doesNotContain("orderTotal");

        assertThat(result.removedFields())
                .anyMatch(field -> field.location().contains("customerEmail"))
                .anyMatch(field -> field.location().contains("orderTotal"));
    }

    @Test
    void sanitizesPiiInsideRetainedStringValues() {
        String json = """
                { "waitEvent": "client backend user@example.com", "locks": 3 }""";
        MetricsSanitizationResult result = sanitizer.sanitize(json);
        assertThat(result.json()).doesNotContain("user@example.com").contains("<REDACTED>");
    }

    @Test
    void nonJsonInputIsRedactedDefensively() {
        MetricsSanitizationResult result = sanitizer.sanitize("free text with a@b.com inside");
        assertThat(result.json()).doesNotContain("a@b.com");
    }

    @Test
    void nullIsSafe() {
        assertThat(sanitizer.sanitize(null).json()).isNull();
    }
}
