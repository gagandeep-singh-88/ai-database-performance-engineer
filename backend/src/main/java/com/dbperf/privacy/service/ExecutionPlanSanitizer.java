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
 * Sanitizes EXPLAIN / EXPLAIN ANALYZE output. Plan structure is what makes a
 * plan useful, so this is deliberately surgical:
 * <ul>
 *   <li><b>Preserved:</b> node types, cost/rows/width, actual time/loops,
 *       buffers, index names, join/sort/aggregate operations, timing.</li>
 *   <li><b>Masked</b> (as $N placeholders): literal comparison values in
 *       Filter/Cond lines, quoted string literals, and any embedded secret
 *       (connection string, token, email, …).</li>
 *   <li><b>Removed:</b> SQL comments (deleted, not placeholdered).</li>
 * </ul>
 * Masked values share the payload's {@link PlaceholderAllocator}, so a value
 * that also appears in the SQL keeps the same $N. Long numbers on node lines
 * (row estimates, costs) are never touched — only literals inside predicate
 * clauses are masked.
 */
@Component
public class ExecutionPlanSanitizer {

    // Leading whitespace tolerated — Postgres indents Filter/Cond lines.
    private static final Pattern CONDITION_LINE = Pattern.compile(
            "(?i)\\s*(Filter|Index Cond|Recheck Cond|Hash Cond|Join Filter|Merge Cond|Index Recheck):\\s*(.*)");

    private static final Pattern QUOTED_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    // Any-length numeric literal inside a predicate clause is a compared value.
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?![\\w.])");
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*$");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private final PiiDetector piiDetector;

    public ExecutionPlanSanitizer(PiiDetector piiDetector, PrivacyProperties properties) {
        this.piiDetector = piiDetector;
    }

    public RedactionResult sanitize(String plan) {
        return sanitize(plan, new PlaceholderAllocator());
    }

    public RedactionResult sanitize(String plan, PlaceholderAllocator allocator) {
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (plan == null || plan.isBlank()) {
            return new RedactionResult(plan, findings);
        }

        // 1. Remove comments entirely (may echo the original query / secrets)
        String text = plan;
        int comments = 0;
        Matcher block = BLOCK_COMMENT.matcher(text);
        while (block.find()) {
            comments++;
        }
        text = BLOCK_COMMENT.matcher(text).replaceAll(" ");

        // 2. High-confidence secrets, excluding numeric heuristics so metrics survive
        RedactionResult secrets = piiDetector.redact(text, PiiDetector.NUMERIC_HEURISTICS, allocator);
        findings.putAll(secrets.findings());
        text = secrets.text();

        // 3. Per-line: strip line comments; mask quoted literals; mask numeric literals
        //    only inside predicate clauses (never on cost/rows node lines).
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int quotedCount = 0;
        int numericCount = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (LINE_COMMENT.matcher(line).find()) {
                comments++;
                line = LINE_COMMENT.matcher(line).replaceAll("");
            }

            Matcher q = QUOTED_LITERAL.matcher(line);
            StringBuilder lineOut = new StringBuilder();
            while (q.find()) {
                String content = q.group().substring(1, q.group().length() - 1);
                if (PlaceholderAllocator.isPlaceholder(content)) {
                    q.appendReplacement(lineOut, Matcher.quoteReplacement(q.group()));
                } else {
                    q.appendReplacement(lineOut, Matcher.quoteReplacement(
                            "'" + allocator.placeholderFor(content, PiiType.STRING_LITERAL) + "'"));
                    quotedCount++;
                }
            }
            q.appendTail(lineOut);
            line = lineOut.toString();

            Matcher cond = CONDITION_LINE.matcher(line);
            if (cond.matches()) {
                Matcher n = NUMERIC_LITERAL.matcher(cond.group(2));
                StringBuilder condOut = new StringBuilder();
                while (n.find()) {
                    n.appendReplacement(condOut, Matcher.quoteReplacement(
                            allocator.placeholderFor(n.group(), PiiType.PREDICATE_LITERAL)));
                    numericCount++;
                }
                n.appendTail(condOut);
                line = line.substring(0, cond.start(2)) + condOut;
            }

            out.append(line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        if (quotedCount > 0) {
            findings.merge(PiiType.STRING_LITERAL, quotedCount, Integer::sum);
        }
        if (numericCount > 0) {
            findings.merge(PiiType.PREDICATE_LITERAL, numericCount, Integer::sum);
        }
        if (comments > 0) {
            findings.merge(PiiType.SQL_COMMENT, comments, Integer::sum);
        }

        return new RedactionResult(out.toString(), findings);
    }
}
