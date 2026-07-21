package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.AiResponseStyle;
import com.dbperf.privacy.domain.SanitizationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Partial update of a user's privacy &amp; AI settings; null fields are left unchanged.
 */
public record UpdatePrivacySettingsRequest(
        Boolean sqlSanitizationEnabled,
        Boolean aiEnabled,
        Boolean payloadValidationEnabled,
        Boolean showPayloadPreview,
        Boolean blockOnPiiDetected,
        SanitizationMode sanitizationMode,
        AiResponseStyle aiResponseStyle,
        @Min(value = 200, message = "Max response length must be at least 200")
        @Max(value = 8000, message = "Max response length must be at most 8000")
        Integer maxResponseLength) {
}
