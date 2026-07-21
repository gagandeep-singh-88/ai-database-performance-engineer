package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PiiType;

/**
 * A single category of sensitive data found in a payload, with how many
 * times it occurred. Deliberately carries no raw value — only the type,
 * a human label and a count — so it is safe to serialize, log and persist.
 *
 * @param type       machine key of the detected category
 * @param label      human-readable label for the UI
 * @param occurrences number of matches redacted
 */
public record PiiFinding(PiiType type, String label, int occurrences) {

    public static PiiFinding of(PiiType type, int occurrences) {
        return new PiiFinding(type, type.label(), occurrences);
    }
}
