package com.dbperf.privacy.dto;

import java.util.List;

/**
 * Everything the Privacy page needs to show the user exactly what will (and
 * will not) be sent to the AI.
 *
 * @param original       the raw payload as submitted
 * @param sanitized      the payload after redaction — exactly what the AI receives
 * @param findings       PII categories detected and masked, with counts
 * @param placeholders   the "$N → category" legend for the masked values
 * @param removedFields  non-placeholder removals (comments, dropped metric keys)
 * @param validation     the validation gate result
 * @param privacyStatus  headline status: PROTECTED, BLOCKED or AI_DISABLED
 */
public record PayloadPreviewResponse(
        PayloadSection original,
        PayloadSection sanitized,
        List<PiiFinding> findings,
        List<PlaceholderMapping> placeholders,
        List<RemovedField> removedFields,
        ValidationResult validation,
        String privacyStatus) {
}
