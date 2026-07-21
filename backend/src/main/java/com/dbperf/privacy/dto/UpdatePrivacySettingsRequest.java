package com.dbperf.privacy.dto;

/**
 * Partial update of a user's privacy settings; null fields are left unchanged.
 */
public record UpdatePrivacySettingsRequest(Boolean sqlSanitizationEnabled, Boolean aiEnabled) {
}
