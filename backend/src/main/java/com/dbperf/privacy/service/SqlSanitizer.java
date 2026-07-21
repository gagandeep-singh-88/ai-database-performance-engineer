package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.RedactionResult;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes raw SQL before it is shown to the AI. Two passes:
 * <ol>
 *   <li>Typed PII/secret detection via {@link PiiDetector} (emails, tokens, …).</li>
 *   <li>A structural pass that masks the CONTENTS of any remaining quoted string
 *       literal and any long numeric literal — the places user data hides in SQL.</li>
 * </ol>
 * Structure is fully preserved: keywords, table names, column names, join
 * conditions and short numeric literals (ids, limits, status codes) are kept,
 * so the AI still sees an accurate query shape to reason about.
 *
 * <pre>
 *   SELECT * FROM customers WHERE email = 'john@example.com'
 *   -&gt; SELECT * FROM customers WHERE email = '&lt;REDACTED&gt;'
 * </pre>
 */
@Component
public class SqlSanitizer {

    // Single-quoted literal, honoring '' escapes: '...''...'
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    // Numeric literals long enough to be identifiers (kept short numbers intact)
    private static final Pattern LONG_NUMERIC_LITERAL = Pattern.compile("(?<![\\w.])\\d{9,}(?![\\w.])");

    private final PiiDetector piiDetector;
    private final String token;

    public SqlSanitizer(PiiDetector piiDetector, PrivacyProperties properties) {
        this.piiDetector = piiDetector;
        this.token = properties.redactionToken();
    }

    public RedactionResult sanitize(String sql) {
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (sql == null || sql.isBlank()) {
            return new RedactionResult(sql, findings);
        }

        // Pass 1: typed detectors
        RedactionResult typed = piiDetector.redact(sql);
        findings.putAll(typed.findings());
        String text = typed.text();

        // Pass 2: mask any remaining quoted-literal contents
        String quoted = "'" + token + "'";
        Matcher matcher = STRING_LITERAL.matcher(text);
        StringBuilder out = new StringBuilder();
        int literals = 0;
        while (matcher.find()) {
            if (matcher.group().equals(quoted)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(quoted));
                literals++;
            }
        }
        matcher.appendTail(out);
        if (literals > 0) {
            findings.merge(PiiType.STRING_LITERAL, literals, Integer::sum);
        }
        text = out.toString();

        // Pass 3: mask long numeric literals not already caught
        Matcher numeric = LONG_NUMERIC_LITERAL.matcher(text);
        StringBuilder numericOut = new StringBuilder();
        int numerics = 0;
        while (numeric.find()) {
            numeric.appendReplacement(numericOut, Matcher.quoteReplacement(token));
            numerics++;
        }
        numeric.appendTail(numericOut);
        if (numerics > 0) {
            findings.merge(PiiType.LONG_NUMERIC_ID, numerics, Integer::sum);
        }

        return new RedactionResult(numericOut.toString(), findings);
    }
}
