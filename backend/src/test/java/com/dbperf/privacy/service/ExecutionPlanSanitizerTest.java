package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.dto.RedactionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPlanSanitizerTest {

    private final PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", true, true);
    private final ExecutionPlanSanitizer sanitizer =
            new ExecutionPlanSanitizer(new PiiDetector(properties), properties);

    private static final String PLAN = """
            Seq Scan on customers  (cost=0.00..2041.00 rows=100000 width=50) (actual time=0.02..18.4 rows=99998 loops=1)
              Filter: ((email)::text = 'john@example.com'::text)
              Rows Removed by Filter: 12345
            Index Cond: (customer_id = 4242)
              Buffers: shared hit=812 read=40""";

    @Test
    void preservesPlanMetricsAndStructure() {
        RedactionResult result = sanitizer.sanitize(PLAN);
        String out = result.text();
        assertThat(out)
                .contains("Seq Scan on customers")
                .contains("cost=0.00..2041.00 rows=100000 width=50")
                .contains("actual time=0.02..18.4 rows=99998 loops=1")
                .contains("Buffers: shared hit=812 read=40");
    }

    @Test
    void masksLiteralValuesInConditions() {
        RedactionResult result = sanitizer.sanitize(PLAN);
        String out = result.text();
        assertThat(out)
                .doesNotContain("john@example.com")   // email literal masked
                .doesNotContain("4242");               // condition literal masked
        assertThat(out).contains("Index Cond:");       // structure kept
    }

    @Test
    void stripsSqlCommentsThatMayEchoData() {
        RedactionResult result = sanitizer.sanitize(
                "Seq Scan on t (cost=0..1 rows=1 width=4) -- user john@example.com ran this");
        assertThat(result.text()).doesNotContain("john@example.com");
    }

    @Test
    void nullPlanIsSafe() {
        assertThat(sanitizer.sanitize(null).text()).isNull();
    }
}
