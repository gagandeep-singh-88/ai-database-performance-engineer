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
    void redactsEmailLiteralWithNumberedPlaceholder() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM customers WHERE email='john@example.com'");
        assertThat(result.text()).isEqualTo("SELECT * FROM customers WHERE email='$1'");
    }

    @Test
    void deduplicatesRepeatedValuesAndNumbersDifferentValuesDistinctly() {
        // The task's acceptance query: both emails -> $1, phone -> $2.
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM customers WHERE email = 'john@example.com' OR email = 'john@example.com' "
                        + "AND phone = '+1-415-555-2671';");
        assertThat(result.text()).isEqualTo(
                "SELECT * FROM customers WHERE email = '$1' OR email = '$1' AND phone = '$2';");
    }

    // --- predicate literals masked consistently, any quoting / any length ---

    @Test
    void masksShortUnquotedNumericInComparison() {
        RedactionResult result = sanitizer.sanitize("SELECT * FROM orders WHERE customer_id = 4242");
        assertThat(result.text()).isEqualTo("SELECT * FROM orders WHERE customer_id = $1");
        assertThat(result.findings()).containsKey(PiiType.PREDICATE_LITERAL);
    }

    @Test
    void masksQuotedNumericLiteral() {
        RedactionResult result = sanitizer.sanitize("SELECT * FROM orders WHERE customer_id = '4242'");
        assertThat(result.text()).isEqualTo("SELECT * FROM orders WHERE customer_id = '$1'");
        assertThat(result.findings()).containsKey(PiiType.STRING_LITERAL);
    }

    @Test
    void masksLongNumericInComparison() {
        RedactionResult result = sanitizer.sanitize("SELECT * FROM accounts WHERE account_no = 123456789");
        assertThat(result.text()).doesNotContain("123456789").contains("account_no = $1");
        assertThat(result.findings()).containsKey(PiiType.PREDICATE_LITERAL);
    }

    @Test
    void allThreeNumericFormsAreMaskedConsistently() {
        assertThat(sanitizer.sanitize("WHERE x = 7").text()).isEqualTo("WHERE x = $1");
        assertThat(sanitizer.sanitize("WHERE x = '7'").text()).isEqualTo("WHERE x = '$1'");
        assertThat(sanitizer.sanitize("WHERE x = 999999999").text()).isEqualTo("WHERE x = $1");
    }

    @Test
    void reusesPlaceholderForSameValueAcrossPredicates() {
        RedactionResult result = sanitizer.sanitize("WHERE a = 4242 OR b = 4242 OR c = 99");
        assertThat(result.text()).isEqualTo("WHERE a = $1 OR b = $1 OR c = $2");
    }

    @Test
    void masksInListAndBetweenBounds() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM t WHERE id IN (11, 22, 33) AND age BETWEEN 18 AND 65");
        assertThat(result.text())
                .contains("IN (")
                .contains("BETWEEN")
                .doesNotContain("11").doesNotContain("22").doesNotContain("33")
                .doesNotContain("18").doesNotContain("65");
        assertThat(result.findings()).containsKey(PiiType.PREDICATE_LITERAL);
    }

    @Test
    void masksLongNumericOutsidePredicateAsIdentifier() {
        RedactionResult result = sanitizer.sanitize("SELECT 123456789 AS big");
        assertThat(result.text()).isEqualTo("SELECT $1 AS big");
        assertThat(result.findings()).containsKey(PiiType.LONG_NUMERIC_ID);
    }

    // --- structure preserved: keywords, tables, columns, joins ---

    @Test
    void preservesJoinsColumnsKeywordsAndLimit() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT c.full_name FROM customers c JOIN orders o ON o.customer_id = c.id "
                        + "WHERE o.status = 'pending' LIMIT 10");
        assertThat(result.text())
                .contains("SELECT c.full_name")
                .contains("FROM customers c")
                .contains("JOIN orders o ON o.customer_id = c.id")  // join identifiers untouched
                .contains("LIMIT 10")                                // clause argument, not a comparison
                .doesNotContain("pending");                          // quoted value masked
    }

    @Test
    void masksArbitraryQuotedStringLiterals() {
        RedactionResult result = sanitizer.sanitize(
                "INSERT INTO users (name) VALUES ('Jane Q. Public')");
        assertThat(result.text()).contains("'$1'").doesNotContain("Jane");
        assertThat(result.findings()).containsKey(PiiType.STRING_LITERAL);
    }

    // --- comment stripping (removed entirely, not placeholdered) ---

    @Test
    void stripsLineCommentsThatMayCarryBusinessContext() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT * FROM orders -- customer John Doe, VIP account\nWHERE region = 'US'");
        assertThat(result.text()).doesNotContain("John Doe").doesNotContain("VIP");
        assertThat(result.findings()).containsKey(PiiType.SQL_COMMENT);
    }

    @Test
    void stripsBlockComments() {
        RedactionResult result = sanitizer.sanitize(
                "SELECT /* tenant: acme-corp confidential */ * FROM orders");
        assertThat(result.text()).doesNotContain("acme-corp");
        assertThat(result.findings()).containsKey(PiiType.SQL_COMMENT);
    }

    @Test
    void nullSqlIsSafe() {
        assertThat(sanitizer.sanitize(null).text()).isNull();
        assertThat(sanitizer.sanitize("  ").isClean()).isTrue();
    }
}
