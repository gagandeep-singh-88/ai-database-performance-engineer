package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.RedactionResult;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical, ordered set of PII/secret detectors. This one class is the
 * single source of truth for what counts as sensitive: both the sanitizers
 * (which redact) and the {@link PayloadValidator} (which blocks on residue)
 * run these exact patterns, so detection and enforcement can never drift.
 *
 * <p>Patterns run most-specific first (e.g. JWT before generic tokens,
 * credit card before long numeric id) so a value is classified by its
 * narrowest matching category. Raw matches are never retained.
 */
@Component
public class PiiDetector {

    // --- structural / high-confidence secrets (run first) ---
    private static final Pattern CONNECTION_STRING = Pattern.compile(
            "(?i)\\b(?:jdbc:)?(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqp)://[^\\s'\"]+");

    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");

    // key = value / key: "value" secrets — value is always the LAST group
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)(?:password|passwd|pwd|api[_-]?key|apikey|secret(?:[_-]?key)?|access[_-]?key|"
                    + "auth[_-]?token|client[_-]?secret|credentials?)\\s*[:=]\\s*['\"]?([^\\s'\",;)]{3,})");

    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)bearer\\s+([A-Za-z0-9._~+/-]{8,}=*)");

    private static final Pattern API_KEY = Pattern.compile(
            "\\b(?:sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,}"
                    + "|gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})\\b");

    // --- personal identifiers ---
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern CREDIT_CARD = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?<![ -])(?!\\d)");

    private static final Pattern AADHAAR = Pattern.compile(
            "(?<!\\d)(?:\\d{4}\\s\\d{4}\\s\\d{4}|\\d{12})(?!\\d)");

    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern IP_ADDRESS = Pattern.compile(
            "(?<![\\w.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\w.])");

    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.])\\+?\\d[\\d\\s().-]{7,}\\d(?![\\w.])");

    private static final Pattern LONG_NUMERIC_ID = Pattern.compile("(?<!\\d)\\d{9,}(?!\\d)");

    private final String token;

    public PiiDetector(PrivacyProperties properties) {
        this.token = properties.redactionToken();
    }

    /**
     * Redact every known PII/secret category from {@code input}.
     *
     * @return sanitized text and the categories masked (with counts)
     */
    public RedactionResult redact(String input) {
        return redact(input, Set.of());
    }

    /**
     * Redact all categories except those in {@code exclude}. Callers that work
     * on structured metric text (execution plans, stats) exclude the numeric
     * heuristics ({@link PiiType#PHONE}, {@link PiiType#LONG_NUMERIC_ID}) so
     * legitimate row/cost figures are preserved.
     */
    public RedactionResult redact(String input, Set<PiiType> exclude) {
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (input == null || input.isEmpty()) {
            return new RedactionResult(input, findings);
        }
        String text = input;
        text = full(PiiType.CONNECTION_STRING, CONNECTION_STRING, text, findings, exclude, m -> true);
        text = full(PiiType.JWT, JWT, text, findings, exclude, m -> true);
        text = lastGroup(PiiType.SECRET, SECRET_KV, text, findings, exclude);
        text = lastGroup(PiiType.BEARER_TOKEN, BEARER_TOKEN, text, findings, exclude);
        text = full(PiiType.API_KEY, API_KEY, text, findings, exclude, m -> true);
        // UUID is highly structured — detect it before the numeric detectors so a
        // 12-digit run inside a UUID isn't mistaken for an Aadhaar/long id.
        text = full(PiiType.UUID, UUID, text, findings, exclude, m -> true);
        text = full(PiiType.EMAIL, EMAIL, text, findings, exclude, m -> true);
        text = full(PiiType.CREDIT_CARD, CREDIT_CARD, text, findings, exclude, m -> luhnValid(m.group()));
        text = full(PiiType.AADHAAR, AADHAAR, text, findings, exclude, m -> true);
        text = full(PiiType.PAN, PAN, text, findings, exclude, m -> true);
        text = full(PiiType.IP_ADDRESS, IP_ADDRESS, text, findings, exclude, m -> true);
        text = full(PiiType.PHONE, PHONE, text, findings, exclude, m -> digitCountInRange(m.group(), 10, 15));
        text = full(PiiType.LONG_NUMERIC_ID, LONG_NUMERIC_ID, text, findings, exclude, m -> true);
        return new RedactionResult(text, findings);
    }

    /** Numeric-heuristic categories that misfire on execution-plan metrics. */
    public static final Set<PiiType> NUMERIC_HEURISTICS =
            EnumSet.of(PiiType.PHONE, PiiType.LONG_NUMERIC_ID);

    /** True if {@code input} contains any detectable sensitive data. */
    public boolean containsSensitiveData(String input) {
        return !redact(input).isClean();
    }

    private String full(PiiType type, Pattern pattern, String text,
                        Map<PiiType, Integer> findings, Set<PiiType> exclude, Predicate<Matcher> accept) {
        if (exclude.contains(type)) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            if (accept.test(matcher)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(token));
                count++;
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        if (count > 0) {
            findings.merge(type, count, Integer::sum);
        }
        return out.toString();
    }

    /**
     * Replace only the last capturing group (the secret value), preserving the
     * key/prefix. Relies on the value being the final group at the match end.
     */
    private String lastGroup(PiiType type, Pattern pattern, String text,
                             Map<PiiType, Integer> findings, Set<PiiType> exclude) {
        if (exclude.contains(type)) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            String full = matcher.group();
            String value = matcher.group(matcher.groupCount());
            String replacement = full.substring(0, full.length() - value.length()) + token;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            count++;
        }
        matcher.appendTail(out);
        if (count > 0) {
            findings.merge(type, count, Integer::sum);
        }
        return out.toString();
    }

    private static boolean digitCountInRange(String value, int min, int max) {
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                digits++;
            }
        }
        return digits >= min && digits <= max;
    }

    /** Luhn checksum — rejects random 13-19 digit runs that aren't real card numbers. */
    private static boolean luhnValid(String candidate) {
        int sum = 0;
        boolean alternate = false;
        int digits = 0;
        for (int i = candidate.length() - 1; i >= 0; i--) {
            char c = candidate.charAt(i);
            if (!Character.isDigit(c)) {
                continue;
            }
            digits++;
            int value = c - '0';
            if (alternate) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            alternate = !alternate;
        }
        return digits >= 13 && sum % 10 == 0;
    }
}
