package com.dbperf.privacy.service;

import com.dbperf.privacy.domain.PiiType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns Postgres-style numbered placeholders ($1, $2, …) to masked literal
 * values, mirroring pg_stat_statements' own query normalization.
 *
 * <p>Numbering is sequential in first-encounter order and <b>deduplicated by
 * value</b>: the same literal always maps to the same number (so a repeated
 * condition or self-join stays visible to the AI), while different values —
 * regardless of PII category — always get different numbers.
 *
 * <p>One allocator instance is shared across a single sanitization pass (the SQL,
 * its plan, and any metrics) so a value that appears in more than one artifact
 * keeps one consistent placeholder. The raw values are held only transiently in
 * memory for deduplication and are never persisted, logged, or exposed.
 */
public class PlaceholderAllocator {

    /** placeholder number → its category and how many times it was masked. */
    public record Entry(int number, PiiType type, int occurrences) {
        public String placeholder() {
            return "$" + number;
        }
    }

    private final Map<String, Integer> numberByValue = new LinkedHashMap<>();
    private final Map<Integer, PiiType> typeByNumber = new LinkedHashMap<>();
    private final Map<Integer, Integer> countByNumber = new LinkedHashMap<>();

    /**
     * @return the placeholder for {@code value} (e.g. "$1"), assigning a new
     * number on first encounter and reusing it on repeats.
     */
    public String placeholderFor(String value, PiiType type) {
        Integer number = numberByValue.get(value);
        if (number == null) {
            number = numberByValue.size() + 1;
            numberByValue.put(value, number);
            typeByNumber.put(number, type);
        }
        countByNumber.merge(number, 1, Integer::sum);
        return "$" + number;
    }

    /** True if {@code text} is already a placeholder, so it is not re-masked. */
    public static boolean isPlaceholder(String text) {
        return text != null && text.matches("\\$\\d+");
    }

    /** Ordered legend (by number) mapping each placeholder to its category — never any raw value. */
    public List<Entry> entries() {
        return typeByNumber.keySet().stream()
                .sorted()
                .map(number -> new Entry(number, typeByNumber.get(number), countByNumber.get(number)))
                .toList();
    }

    public boolean isEmpty() {
        return numberByValue.isEmpty();
    }
}
