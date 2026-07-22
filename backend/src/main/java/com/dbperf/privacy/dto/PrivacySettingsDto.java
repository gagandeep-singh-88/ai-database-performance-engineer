package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.AiResponseStyle;
import com.dbperf.privacy.domain.PrivacySettings;
import com.dbperf.privacy.domain.SanitizationMode;

import java.time.Instant;

public record PrivacySettingsDto(
        boolean sqlSanitizationEnabled,
        boolean aiEnabled,
        boolean payloadValidationEnabled,
        boolean showPayloadPreview,
        boolean blockOnPiiDetected,
        SanitizationMode sanitizationMode,
        AiResponseStyle aiResponseStyle,
        int maxResponseLength,
        Instant updatedAt) {

    public static PrivacySettingsDto from(PrivacySettings settings) {
        return new PrivacySettingsDto(
                settings.isSqlSanitizationEnabled(),
                settings.isAiEnabled(),
                settings.isPayloadValidationEnabled(),
                settings.isShowPayloadPreview(),
                settings.isBlockOnPiiDetected(),
                settings.getSanitizationMode(),
                settings.getAiResponseStyle(),
                settings.getMaxResponseLength(),
                settings.getUpdatedAt());
    }
}
