package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PiiType;

import java.util.List;
import java.util.Map;

/**
 * Output of a redaction pass: the sanitized text plus the set of PII
 * categories that were masked, with counts. Never contains raw values.
 *
 * @param text     sanitized text, safe to forward to the AI
 * @param findings detected categories mapped to occurrence counts
 */
public record RedactionResult(String text, Map<PiiType, Integer> findings) {

    public boolean isClean() {
        return findings.isEmpty();
    }

    public int totalRedactions() {
        return findings.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<PiiFinding> toFindings() {
        return findings.entrySet().stream()
                .map(entry -> PiiFinding.of(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(b.occurrences(), a.occurrences()))
                .toList();
    }
}
