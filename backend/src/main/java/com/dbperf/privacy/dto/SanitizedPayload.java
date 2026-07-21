package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PiiType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The fully sanitized analysis payload plus everything the engine did to it.
 * The {@code sql}/{@code plan}/{@code schemaContext} fields are exactly what is
 * forwarded to the AI; the findings/removed lists explain the redactions.
 *
 * @param sql            sanitized SQL (may be null)
 * @param plan           sanitized execution plan (may be null)
 * @param schemaContext  sanitized schema/statistics context (may be null)
 * @param findings       PII categories masked, aggregated across all sections
 * @param removedFields  user-facing explanations for the preview panel
 * @param validation     validation outcome for this payload
 */
public record SanitizedPayload(
        String sql,
        String plan,
        String schemaContext,
        List<PiiFinding> findings,
        List<RemovedField> removedFields,
        ValidationResult validation) {

    /** Aggregate several per-section redaction maps into one findings list. */
    public static List<PiiFinding> mergeFindings(List<Map<PiiType, Integer>> maps) {
        Map<PiiType, Integer> merged = new EnumMap<>(PiiType.class);
        maps.forEach(map -> map.forEach((type, count) -> merged.merge(type, count, Integer::sum)));
        List<PiiFinding> findings = new ArrayList<>();
        merged.forEach((type, count) -> findings.add(PiiFinding.of(type, count)));
        findings.sort((a, b) -> Integer.compare(b.occurrences(), a.occurrences()));
        return findings;
    }
}
