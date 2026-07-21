package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadValidatorTest {

    private PayloadValidator validator(boolean block) {
        PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", block, true);
        return new PayloadValidator(new PiiDetector(properties), properties);
    }

    @Test
    void cleanPayloadPasses() {
        ValidationResult result = validator(true).validate(
                "SELECT * FROM customers WHERE email = '$1'", true);
        assertThat(result.passed()).isTrue();
        assertThat(result.residualFindings()).isEmpty();
    }

    @Test
    void residualPiiBlocksTheRequest() {
        ValidationResult result = validator(true).validate(
                "SELECT * FROM customers WHERE email = 'john@example.com'", true);
        assertThat(result.passed()).isFalse();
        assertThat(result.residualFindings()).anyMatch(f -> f.type() == PiiType.EMAIL);
    }

    @Test
    void aiDisabledFailsClosedWithoutInspectingContent() {
        ValidationResult result = validator(true).validate("anything", false);
        assertThat(result.passed()).isFalse();
        assertThat(result.aiEnabled()).isFalse();
    }

    @Test
    void residualPiiAllowedWhenBlockingDisabledByConfig() {
        ValidationResult result = validator(false).validate("email john@example.com", true);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void planMetricsAreNotTreatedAsResidualPii() {
        ValidationResult result = validator(true).validate(
                "Seq Scan on orders (cost=0.00..12500.00 rows=100000000 width=64)", true);
        assertThat(result.passed()).isTrue();
    }
}
