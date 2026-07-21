package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.RedactionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectorTest {

    private final PiiDetector detector =
            new PiiDetector(new PrivacyProperties(true, "<REDACTED>", true, true));

    @Test
    void redactsEmailAddresses() {
        RedactionResult result = detector.redact("contact john.doe@example.com now");
        assertThat(result.text()).isEqualTo("contact $1 now");
        assertThat(result.findings()).containsEntry(PiiType.EMAIL, 1);
    }

    @Test
    void redactsJwtBeforeGenericTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0";
        RedactionResult result = detector.redact("Authorization token " + jwt);
        assertThat(result.text()).doesNotContain("eyJ").contains("$1");
        assertThat(result.findings()).containsKey(PiiType.JWT);
    }

    @Test
    void redactsBearerTokenPreservingKeyword() {
        RedactionResult result = detector.redact("Authorization: Bearer abcDEF123456ghiJKL");
        assertThat(result.text()).isEqualTo("Authorization: Bearer $1");
        assertThat(result.findings()).containsKey(PiiType.BEARER_TOKEN);
    }

    @Test
    void redactsKeyValueSecretsPreservingKey() {
        RedactionResult result = detector.redact("password=SuperSecret123 and api_key: sk-abc12345");
        assertThat(result.text()).contains("password=$1");
        assertThat(result.findings()).containsKey(PiiType.SECRET);
    }

    @Test
    void redactsConnectionStrings() {
        RedactionResult result = detector.redact(
                "jdbc:postgresql://user:pass@db.internal:5432/prod is the target");
        assertThat(result.text()).doesNotContain("pass@db.internal").contains("$1");
        assertThat(result.findings()).containsKey(PiiType.CONNECTION_STRING);
    }

    @Test
    void redactsValidCreditCardButNotRandomDigits() {
        RedactionResult card = detector.redact("card 4111 1111 1111 1111 charged");
        assertThat(card.findings()).containsKey(PiiType.CREDIT_CARD);

        // Fails Luhn -> classified as a long numeric id, not a card
        RedactionResult notCard = detector.redact("ref 1234 5678 9012 3456 stored");
        assertThat(notCard.findings()).doesNotContainKey(PiiType.CREDIT_CARD);
    }

    @Test
    void redactsAadhaarPanAndUuid() {
        assertThat(detector.redact("aadhaar 1234 5678 9012").findings()).containsKey(PiiType.AADHAAR);
        assertThat(detector.redact("PAN ABCDE1234F here").findings()).containsKey(PiiType.PAN);
        assertThat(detector.redact("id 550e8400-e29b-41d4-a716-446655440000").findings())
                .containsKey(PiiType.UUID);
    }

    @Test
    void redactsIpAddresses() {
        RedactionResult result = detector.redact("client 192.168.10.24 connected");
        assertThat(result.text()).isEqualTo("client $1 connected");
        assertThat(result.findings()).containsKey(PiiType.IP_ADDRESS);
    }

    @Test
    void redactsPhoneNumbers() {
        assertThat(detector.redact("call +1 (415) 555-2671 today").findings())
                .containsKey(PiiType.PHONE);
    }

    @Test
    void numberingIsSequentialAndDeduplicatedByValue() {
        // same email twice -> same $1; a different value -> $2
        RedactionResult result = detector.redact("a@x.com then a@x.com and b@y.com");
        assertThat(result.text()).isEqualTo("$1 then $1 and $2");
    }

    @Test
    void excludeSetPreservesLongNumbersForPlans() {
        RedactionResult result = detector.redact("rows=100000000 width=50",
                PiiDetector.NUMERIC_HEURISTICS);
        assertThat(result.text()).contains("100000000");
        assertThat(result.isClean()).isTrue();
    }

    @Test
    void cleanTextProducesNoFindings() {
        RedactionResult result = detector.redact("SELECT count(*) FROM orders WHERE status = 'paid'");
        assertThat(result.isClean()).isTrue();
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(detector.redact(null).text()).isNull();
        assertThat(detector.redact("").isClean()).isTrue();
        assertThat(detector.containsSensitiveData("no pii here")).isFalse();
        assertThat(detector.containsSensitiveData("email a@b.com")).isTrue();
    }
}
