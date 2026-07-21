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
 *   <li><b>Removed:</b> literal comparison values in Filter/Cond lines,
 *       quoted string literals, SQL comments, and any embedded secret
 *       (connection string, token, email, …).</li>
 * </ul>
 * Long numbers on node lines (row estimates, costs) are never touched, because
 * the numeric heuristics are excluded — only literals inside predicate clauses
 * are masked.
 */
@Component
public class ExecutionPlanSanitizer {

    // Leading whitespace tolerated — Postgres indents Filter/Cond lines.
    private static final Pattern CONDITION_LINE = Pattern.compile(
            "(?i)\\s*(Filter|Index Cond|Recheck Cond|Hash Cond|Join Filter|Merge Cond|Index Recheck):\\s*(.*)");

    private static final Pattern QUOTED_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("(?<![\\w.])\\d{3,}(?![\\w.])");
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*$");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private final PiiDetector piiDetector;
    private final String token;

    public ExecutionPlanSanitizer(PiiDetector piiDetector, PrivacyProperties properties) {
        this.piiDetector = piiDetector;
        this.token = properties.redactionToken();
    }

    public RedactionResult sanitize(String plan) {
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (plan == null || plan.isBlank()) {
            return new RedactionResult(plan, findings);
        }

        // 1. Strip comments (may echo the original query / secrets)
        String text = BLOCK_COMMENT.matcher(plan).replaceAll(Matcher.quoteReplacement(token));

        // 2. High-confidence secrets, excluding numeric heuristics so metrics survive
        RedactionResult secrets = piiDetector.redact(text, PiiDetector.NUMERIC_HEURISTICS);
        findings.putAll(secrets.findings());
        text = secrets.text();

        // 3. Per-line: mask quoted literals everywhere; mask numeric literals only
        //    inside predicate clauses (never on cost/rows node lines) and line comments.
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int quotedCount = 0;
        int numericCount = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = LINE_COMMENT.matcher(lines[i]).replaceAll(Matcher.quoteReplacement(token));

            String quoted = "'" + token + "'";
            Matcher q = QUOTED_LITERAL.matcher(line);
            StringBuilder lineOut = new StringBuilder();
            while (q.find()) {
                if (!q.group().equals(quoted)) {
                    quotedCount++;
                }
                q.appendReplacement(lineOut, Matcher.quoteReplacement(quoted));
            }
            q.appendTail(lineOut);
            line = lineOut.toString();

            Matcher cond = CONDITION_LINE.matcher(line);
            if (cond.matches()) {
                Matcher n = NUMERIC_LITERAL.matcher(cond.group(2));
                StringBuilder condOut = new StringBuilder();
                while (n.find()) {
                    n.appendReplacement(condOut, Matcher.quoteReplacement(token));
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
            findings.merge(PiiType.LONG_NUMERIC_ID, numericCount, Integer::sum);
        }

        return new RedactionResult(out.toString(), findings);
    }
}
