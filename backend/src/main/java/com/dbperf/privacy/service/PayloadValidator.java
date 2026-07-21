package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.dto.PiiFinding;
import com.dbperf.privacy.dto.RedactionResult;
import com.dbperf.privacy.dto.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Defense-in-depth gate that runs the canonical {@link PiiDetector} over the
 * FINAL, already-sanitized payload. Because sanitizers and this validator use
 * the exact same detectors, a clean sanitization always passes; anything that
 * slipped through (e.g. a pattern a sanitizer chose not to apply) is caught
 * here and the request is blocked before it reaches the AI provider.
 *
 * <p>Numeric heuristics are excluded here: legitimate execution-plan metrics
 * (row estimates, costs) are long numbers and must not be treated as residual
 * PII once the structural sanitizers have run.
 */
@Component
@RequiredArgsConstructor
public class PayloadValidator {

    private final PiiDetector piiDetector;
    private final PrivacyProperties properties;

    /**
     * @param sanitizedPayload the concatenated text that would be sent to the AI
     * @param aiEnabled        whether the user's settings permit AI at all
     */
    public ValidationResult validate(String sanitizedPayload, boolean aiEnabled) {
        if (!aiEnabled) {
            return ValidationResult.aiDisabled();
        }
        RedactionResult residual = piiDetector.redact(sanitizedPayload, PiiDetector.NUMERIC_HEURISTICS);
        if (residual.isClean() || !properties.blockOnResidualPii()) {
            return ValidationResult.clean();
        }
        List<PiiFinding> findings = residual.toFindings();
        return ValidationResult.blocked(findings);
    }
}
