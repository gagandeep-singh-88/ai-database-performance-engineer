package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadValidatorTest {

    private PayloadValidator validator() {
        PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", true, true);
        return new PayloadValidator(new PiiDetector(properties));
    }

    @Test
    void cleanPayloadPasses() {
        ValidationResult result = validator().validate(
                "SELECT * FROM customers WHERE email = '$1'", true, true, true);
        assertThat(result.passed()).isTrue();
        assertThat(result.residualFindings()).isEmpty();
    }

    @Test
    void residualPiiBlocksTheRequestWhenBlockOnPiiEnabled() {
        ValidationResult result = validator().validate(
                "SELECT * FROM customers WHERE email = 'john@example.com'", true, true, true);
        assertThat(result.passed()).isFalse();
        assertThat(result.residualFindings()).anyMatch(f -> f.type() == PiiType.EMAIL);
    }

    @Test
    void aiDisabledFailsClosedWithoutInspectingContent() {
        ValidationResult result = validator().validate("anything", false, true, true);
        assertThat(result.passed()).isFalse();
        assertThat(result.aiEnabled()).isFalse();
    }

    @Test
    void residualPiiAllowedWhenBlockOnPiiDisabled() {
        ValidationResult result = validator().validate("email john@example.com", true, true, false);
        assertThat(result.passed()).isTrue();
        assertThat(result.residualFindings()).isNotEmpty();
    }

    @Test
    void planMetricsAreNotTreatedAsResidualPii() {
        ValidationResult result = validator().validate(
                "Seq Scan on orders (cost=0.00..12500.00 rows=100000000 width=64)", true, true, true);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void validationDisabledSkipsResidualScanEvenWithPii() {
        ValidationResult result = validator().validate(
                "SELECT * FROM customers WHERE email = 'john@example.com'", true, false, true);
        assertThat(result.passed()).isTrue();
        assertThat(result.residualFindings()).isEmpty();
    }
}
