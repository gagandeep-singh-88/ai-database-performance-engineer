package com.dbperf.privacy.dto;

import java.util.List;

/**
 * Outcome of validating a sanitized payload just before the AI call.
 *
 * @param passed            true if the payload is safe to send
 * @param aiEnabled         whether AI is permitted by the user's privacy settings
 * @param residualFindings  any PII still detected after sanitization (should be empty)
 * @param message           human-readable summary for the UI / logs
 */
public record ValidationResult(boolean passed, boolean aiEnabled,
                               List<PiiFinding> residualFindings, String message) {

    public static ValidationResult clean() {
        return new ValidationResult(true, true, List.of(), "Payload passed validation — safe to send to the AI");
    }

    public static ValidationResult aiDisabled() {
        return new ValidationResult(false, false, List.of(),
                "AI is disabled in your privacy settings — no data will be sent to the AI");
    }

    public static ValidationResult blocked(List<PiiFinding> residual) {
        return new ValidationResult(false, true, residual,
                "Blocked: sensitive data was still present after sanitization");
    }
}
