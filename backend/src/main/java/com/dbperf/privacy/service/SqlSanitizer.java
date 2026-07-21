package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.RedactionResult;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes raw SQL before it is shown to the AI. Masked literal values are
 * replaced with Postgres-style numbered placeholders ($1, $2, …) via a shared
 * {@link PlaceholderAllocator}, so identical values reuse one placeholder and
 * the AI can still tell a repeated condition from genuinely different values.
 *
 * <p>Passes, in order:
 * <ol>
 *   <li><b>Strip comments</b> — {@code -- line} and {@code /* block *}{@code /}
 *       comments are removed outright (not placeholdered): they carry names or
 *       business context and have no position/count worth preserving.</li>
 *   <li><b>Typed PII/secret detection</b> via {@link PiiDetector}.</li>
 *   <li><b>Quoted string literals</b> — the contents of every {@code '...'} literal.</li>
 *   <li><b>Predicate comparison values</b> — value side of a comparison, an
 *       {@code IN (...)} list, or {@code BETWEEN a AND b}, masked unconditionally.</li>
 *   <li><b>Long numeric identifiers</b> — any remaining 9+ digit run.</li>
 * </ol>
 * Keywords, table/column names and join conditions ({@code ON a.id = b.id}) are
 * preserved — only compared <em>values</em> are masked.
 */
@Component
public class SqlSanitizer {

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    private static final Pattern COMPARISON_VALUE = Pattern.compile(
            "(<=|>=|<>|!=|=|<|>)(\\s*)(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern IN_LIST = Pattern.compile("(?i)\\bIN\\s*\\(([^)]*)\\)");
    private static final Pattern BETWEEN_BOUNDS = Pattern.compile(
            "(?i)(\\bBETWEEN\\s+)(-?\\d+(?:\\.\\d+)?)(\\s+AND\\s+)(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern BARE_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static final Pattern LONG_NUMERIC_LITERAL = Pattern.compile("(?<![\\w.])\\d{9,}(?![\\w.])");

    private final PiiDetector piiDetector;

    public SqlSanitizer(PiiDetector piiDetector, PrivacyProperties properties) {
        this.piiDetector = piiDetector;
    }

    /** Sanitize with a fresh placeholder set (numbering restarts at $1). */
    public RedactionResult sanitize(String sql) {
        return sanitize(sql, new PlaceholderAllocator());
    }

    /** Sanitize sharing {@code allocator} so values are numbered consistently with the rest of the payload. */
    public RedactionResult sanitize(String sql, PlaceholderAllocator allocator) {
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (sql == null || sql.isBlank()) {
            return new RedactionResult(sql, findings);
        }

        // Pass 0: strip SQL comments entirely
        String text = stripComments(sql, findings);

        // Pass 1: typed detectors (emails, tokens, connection strings, …).
        // Long numerics are left for the predicate/long passes so a long value in a
        // comparison is classified as a predicate literal, not a bare id.
        RedactionResult typed = piiDetector.redact(text, Set.of(PiiType.LONG_NUMERIC_ID), allocator);
        merge(findings, typed.findings());
        text = typed.text();

        // Pass 2: quoted-literal contents
        text = maskQuotedLiterals(text, findings, allocator);

        // Pass 3: predicate comparison values (any length, unquoted)
        text = maskPredicateValues(text, findings, allocator);

        // Pass 4: long numeric identifiers left anywhere else
        text = maskLongNumerics(text, findings, allocator);

        return new RedactionResult(text, findings);
    }

    private String stripComments(String sql, Map<PiiType, Integer> findings) {
        int[] count = {0};
        String noBlock = removeCounting(BLOCK_COMMENT, sql, " ", count);
        String noLine = removeCounting(LINE_COMMENT, noBlock, "", count);
        if (count[0] > 0) {
            findings.merge(PiiType.SQL_COMMENT, count[0], Integer::sum);
        }
        return noLine;
    }

    private String maskQuotedLiterals(String text, Map<PiiType, Integer> findings, PlaceholderAllocator allocator) {
        Matcher matcher = STRING_LITERAL.matcher(text);
        StringBuilder out = new StringBuilder();
        int literals = 0;
        while (matcher.find()) {
            String content = matcher.group().substring(1, matcher.group().length() - 1);
            if (PlaceholderAllocator.isPlaceholder(content)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out,
                        Matcher.quoteReplacement("'" + allocator.placeholderFor(content, PiiType.STRING_LITERAL) + "'"));
                literals++;
            }
        }
        matcher.appendTail(out);
        if (literals > 0) {
            findings.merge(PiiType.STRING_LITERAL, literals, Integer::sum);
        }
        return out.toString();
    }

    private String maskPredicateValues(String text, Map<PiiType, Integer> findings, PlaceholderAllocator allocator) {
        int[] count = {0};

        // col <op> <number>  →  col <op> $N   (operator + spacing preserved)
        Matcher cmp = COMPARISON_VALUE.matcher(text);
        StringBuilder cmpOut = new StringBuilder();
        while (cmp.find()) {
            String ph = allocator.placeholderFor(cmp.group(3), PiiType.PREDICATE_LITERAL);
            cmp.appendReplacement(cmpOut, Matcher.quoteReplacement(cmp.group(1) + cmp.group(2) + ph));
            count[0]++;
        }
        cmp.appendTail(cmpOut);
        text = cmpOut.toString();

        // BETWEEN a AND b  →  BETWEEN $N AND $M
        Matcher between = BETWEEN_BOUNDS.matcher(text);
        StringBuilder betweenOut = new StringBuilder();
        while (between.find()) {
            String low = allocator.placeholderFor(between.group(2), PiiType.PREDICATE_LITERAL);
            String high = allocator.placeholderFor(between.group(4), PiiType.PREDICATE_LITERAL);
            between.appendReplacement(betweenOut,
                    Matcher.quoteReplacement(between.group(1) + low + between.group(3) + high));
            count[0] += 2;
        }
        between.appendTail(betweenOut);
        text = betweenOut.toString();

        // IN (a, b, c)  →  IN ($N, $M, ...)  (numeric members only)
        Matcher in = IN_LIST.matcher(text);
        StringBuilder inOut = new StringBuilder();
        while (in.find()) {
            String masked = maskNumbers(in.group(1), allocator, count);
            in.appendReplacement(inOut, Matcher.quoteReplacement("IN (" + masked + ")"));
        }
        in.appendTail(inOut);
        text = inOut.toString();

        if (count[0] > 0) {
            findings.merge(PiiType.PREDICATE_LITERAL, count[0], Integer::sum);
        }
        return text;
    }

    private String maskLongNumerics(String text, Map<PiiType, Integer> findings, PlaceholderAllocator allocator) {
        Matcher matcher = LONG_NUMERIC_LITERAL.matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(allocator.placeholderFor(matcher.group(), PiiType.LONG_NUMERIC_ID)));
            count++;
        }
        matcher.appendTail(out);
        if (count > 0) {
            findings.merge(PiiType.LONG_NUMERIC_ID, count, Integer::sum);
        }
        return out.toString();
    }

    private String maskNumbers(String text, PlaceholderAllocator allocator, int[] count) {
        Matcher matcher = BARE_NUMBER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(allocator.placeholderFor(matcher.group(), PiiType.PREDICATE_LITERAL)));
            count[0]++;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Replace every match with a fixed {@code replacement}, accumulating the count. */
    private static String removeCounting(Pattern pattern, String text, String replacement, int[] count) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            count[0]++;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void merge(Map<PiiType, Integer> into, Map<PiiType, Integer> from) {
        from.forEach((type, value) -> into.merge(type, value, Integer::sum));
    }
}
