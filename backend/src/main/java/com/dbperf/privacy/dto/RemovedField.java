package com.dbperf.privacy.dto;

/**
 * A single, user-facing explanation of something the engine removed or masked,
 * for the "Fields Removed / Reason for Removal" panel. Carries no raw value.
 *
 * @param location    where it was found, e.g. "SQL", "Execution plan", "metrics.customerEmail"
 * @param category    what kind of data it was, e.g. "Email address", "Rejected field"
 * @param reason      plain-English reason it was removed
 * @param occurrences how many values were affected (1 for a dropped field)
 */
public record RemovedField(String location, String category, String reason, int occurrences) {
}
