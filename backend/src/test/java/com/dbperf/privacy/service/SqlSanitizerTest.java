package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.RedactionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlSanitizerTest {

    private final PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", true, true);
    private final SqlSanitizer sanitizer = new SqlSanitizer(new PiiDetector(properties), properties);

    @Test
    void redactsEmailLiteralExactlyAsSpecified() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM customers WHERE email='john@example.com'");
        assertThat(result.text()).isEqualTo("SELECT * FROM customers WHERE email='<REDACTED>'");
    }

    @Test
    void preservesStructureTableAndColumnNames() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT c.full_name FROM customers c JOIN orders o ON o.customer_id = c.id "
                        + "WHERE c.email = 'a@b.com' AND o.status = 'pending' LIMIT 10");
        String out = result.text();
        assertThat(out)
                .contains("SELECT c.full_name")
                .contains("FROM customers c")
                .contains("JOIN orders o ON o.customer_id = c.id")
                .contains("LIMIT 10")            // short numeric literal preserved
                .doesNotContain("a@b.com")
                .doesNotContain("pending");       // string literal masked
    }

    @Test
    void masksArbitraryQuotedStringLiterals() {
        RedactionResult result = sanitizer.sanitize(
                "INSERT INTO users (name) VALUES ('Jane Q. Public')");
        assertThat(result.text()).contains("'<REDACTED>'").doesNotContain("Jane");
        assertThat(result.findings()).containsKey(PiiType.STRING_LITERAL);
    }

    @Test
    void masksLongNumericIdentifiersButKeepsShortNumbers() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM accounts WHERE account_no = 123456789012 AND active = 1");
        assertThat(result.text()).doesNotContain("123456789012").contains("active = 1");
    }

    @Test
    void nullSqlIsSafe() {
        assertThat(sanitizer.sanitize(null).text()).isNull();
        assertThat(sanitizer.sanitize("  ").isClean()).isTrue();
    }
}
