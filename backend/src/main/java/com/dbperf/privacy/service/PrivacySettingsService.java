package com.dbperf.privacy.service;

import com.dbperf.privacy.domain.PrivacySettings;
import com.dbperf.privacy.dto.PrivacySettingsDto;
import com.dbperf.privacy.dto.UpdatePrivacySettingsRequest;
import com.dbperf.privacy.repository.PrivacySettingsRepository;
import com.dbperf.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages per-user privacy toggles. Missing settings resolve to the safe
 * defaults (sanitization on, AI on) without persisting a row until the user
 * changes something.
 */
@Service
@RequiredArgsConstructor
public class PrivacySettingsService {

    private final PrivacySettingsRepository settingsRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PrivacySettings resolve(UUID userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> PrivacySettings.builder().userId(userId).build());
    }

    @Transactional(readOnly = true)
    public PrivacySettingsDto current() {
        return PrivacySettingsDto.from(resolve(currentUserService.require().getId()));
    }

    @Transactional
    public PrivacySettingsDto update(UpdatePrivacySettingsRequest request) {
        UUID userId = currentUserService.require().getId();
        PrivacySettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> PrivacySettings.builder().userId(userId).build());
        if (request.sqlSanitizationEnabled() != null) {
            settings.setSqlSanitizationEnabled(request.sqlSanitizationEnabled());
        }
        if (request.aiEnabled() != null) {
            settings.setAiEnabled(request.aiEnabled());
        }
        if (request.payloadValidationEnabled() != null) {
            settings.setPayloadValidationEnabled(request.payloadValidationEnabled());
        }
        if (request.showPayloadPreview() != null) {
            settings.setShowPayloadPreview(request.showPayloadPreview());
        }
        if (request.blockOnPiiDetected() != null) {
            settings.setBlockOnPiiDetected(request.blockOnPiiDetected());
        }
        if (request.sanitizationMode() != null) {
            settings.setSanitizationMode(request.sanitizationMode());
        }
        if (request.aiResponseStyle() != null) {
            settings.setAiResponseStyle(request.aiResponseStyle());
        }
        if (request.maxResponseLength() != null) {
            settings.setMaxResponseLength(request.maxResponseLength());
        }
        return PrivacySettingsDto.from(settingsRepository.save(settings));
    }
}
