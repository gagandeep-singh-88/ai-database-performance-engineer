package com.dbperf.privacy.service;

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
 *
 * <p>Both {@code validationEnabled} and {@code blockOnPii} are per-user
 * settings ({@link com.dbperf.privacy.domain.PrivacySettings}) — this class
 * has no global config of its own.
 */
@Component
@RequiredArgsConstructor
public class PayloadValidator {

    private final PiiDetector piiDetector;

    /**
     * @param sanitizedPayload  the concatenated text that would be sent to the AI
     * @param aiEnabled         whether the user's settings permit AI at all
     * @param validationEnabled whether the residual-PII re-scan should run at all
     * @param blockOnPii        when residual PII is found, block (true) or allow-with-warning (false)
     */
    public ValidationResult validate(String sanitizedPayload, boolean aiEnabled,
                                     boolean validationEnabled, boolean blockOnPii) {
        if (!aiEnabled) {
            return ValidationResult.aiDisabled();
        }
        if (!validationEnabled) {
            return ValidationResult.validationDisabled();
        }
        RedactionResult residual = piiDetector.redact(sanitizedPayload, PiiDetector.NUMERIC_HEURISTICS);
        if (residual.isClean()) {
            return ValidationResult.clean();
        }
        List<PiiFinding> findings = residual.toFindings();
        return blockOnPii ? ValidationResult.blocked(findings) : ValidationResult.allowedWithResidualPii(findings);
    }
}
