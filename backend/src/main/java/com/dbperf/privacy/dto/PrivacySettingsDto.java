package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PrivacySettings;

import java.time.Instant;

public record PrivacySettingsDto(boolean sqlSanitizationEnabled, boolean aiEnabled, Instant updatedAt) {

    public static PrivacySettingsDto from(PrivacySettings settings) {
        return new PrivacySettingsDto(settings.isSqlSanitizationEnabled(),
                settings.isAiEnabled(), settings.getUpdatedAt());
    }
}
