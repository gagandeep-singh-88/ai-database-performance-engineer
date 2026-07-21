package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.service.PlaceholderAllocator;

/**
 * A single entry of the "$N → category" legend shown on the preview panel, so
 * the user can see what each placeholder in the sanitized text stood for —
 * without ever seeing the value itself.
 *
 * @param placeholder e.g. "$1"
 * @param category    human label of the detected category, e.g. "Email address"
 * @param occurrences how many times this value appeared (reuse count)
 */
public record PlaceholderMapping(String placeholder, String category, int occurrences) {

    public static PlaceholderMapping from(PlaceholderAllocator.Entry entry) {
        PiiType type = entry.type();
        return new PlaceholderMapping(entry.placeholder(), type.label(), entry.occurrences());
    }
}
