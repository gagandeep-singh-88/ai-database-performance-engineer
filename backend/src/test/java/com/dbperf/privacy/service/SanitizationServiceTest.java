package com.dbperf.privacy.service;

import com.dbperf.common.exception.SensitiveDataException;
import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.domain.PrivacySettings;
import com.dbperf.privacy.dto.PayloadPreviewRequest;
import com.dbperf.privacy.dto.PayloadPreviewResponse;
import com.dbperf.privacy.dto.SanitizedPayload;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests with REAL sanitizers/validator (only persistence and the
 * current-user lookup are mocked) so the end-to-end redact→validate→audit flow
 * is genuinely exercised.
 */
class SanitizationServiceTest {

    private final PrivacyProperties properties = new PrivacyProperties(true, "<REDACTED>", true, true);
    private final PiiDetector piiDetector = new PiiDetector(properties);

    private PrivacySettingsService settingsService;
    private SanitizationAuditService auditService;
    private CurrentUserService currentUserService;
    private SanitizationService service;

    private final User user = User.builder().id(UUID.randomUUID()).email("dev@acme.com")
            .passwordHash("h").fullName("Dev").role(Role.USER).build();

    @BeforeEach
    void setUp() {
        settingsService = mock(PrivacySettingsService.class);
        auditService = mock(SanitizationAuditService.class);
        currentUserService = mock(CurrentUserService.class);
        service = new SanitizationService(
                new SqlSanitizer(piiDetector, properties),
                new ExecutionPlanSanitizer(piiDetector, properties),
                new MetricsSanitizer(piiDetector, new ObjectMapper()),
                new PayloadValidator(piiDetector),
                piiDetector, settingsService, auditService, currentUserService, properties);
    }

    private void settings(boolean sqlSanitization, boolean aiEnabled) {
        when(settingsService.resolve(any())).thenReturn(PrivacySettings.builder()
                .userId(user.getId())
                .sqlSanitizationEnabled(sqlSanitization)
                .aiEnabled(aiEnabled)
                .build());
    }

    private void settingsWithMode(com.dbperf.privacy.domain.SanitizationMode mode) {
        when(settingsService.resolve(any())).thenReturn(PrivacySettings.builder()
                .userId(user.getId())
                .sqlSanitizationEnabled(true)
                .aiEnabled(true)
                .sanitizationMode(mode)
                .build());
    }

    @Test
    void enforceSanitizesAndAuditsWhenClean() {
        settings(true, true);
        SanitizedPayload payload = service.enforceForAnalysis(user, null,
                "SELECT * FROM customers WHERE email='john@example.com'", null, null);

        assertThat(payload.sql()).isEqualTo("SELECT * FROM customers WHERE email='$1'");
        assertThat(payload.validation().passed()).isTrue();
        assertThat(payload.findings()).anyMatch(f -> f.type() == PiiType.EMAIL);
        assertThat(payload.placeholders()).anyMatch(p -> p.placeholder().equals("$1")
                && p.category().equals(PiiType.EMAIL.label()));
        verify(auditService).record(eq(user.getId()), eq(user.getEmail()), isNull(),
                any(), anyInt(), anyLong(), any());
    }

    @Test
    void enforceBlocksWhenSanitizationDisabledButPiiPresent() {
        settings(false, true); // user turned sanitization off — validator still protects them
        assertThatThrownBy(() -> service.enforceForAnalysis(user, null,
                "SELECT * FROM customers WHERE email='john@example.com'", null, null))
                .isInstanceOf(SensitiveDataException.class);
        // audit is still written for the blocked attempt
        verify(auditService).record(any(), any(), isNull(), any(), anyInt(), anyLong(), any());
    }

    @Test
    void enforceBlocksWhenAiDisabled() {
        settings(true, false);
        assertThatThrownBy(() -> service.enforceForAnalysis(user, null, "SELECT 1", null, null))
                .isInstanceOf(SensitiveDataException.class);
    }

    @Test
    void strictModeBlocksWhenRawSqlContainsPiiEvenThoughItWouldSanitizeCleanly() {
        settingsWithMode(com.dbperf.privacy.domain.SanitizationMode.STRICT_BLOCK);
        assertThatThrownBy(() -> service.enforceForAnalysis(user, null,
                "SELECT * FROM customers WHERE email='john@example.com'", null, null))
                .isInstanceOf(SensitiveDataException.class);
    }

    @Test
    void strictModeAllowsCleanSql() {
        settingsWithMode(com.dbperf.privacy.domain.SanitizationMode.STRICT_BLOCK);
        SanitizedPayload payload = service.enforceForAnalysis(user, null, "SELECT id FROM orders", null, null);
        assertThat(payload.validation().passed()).isTrue();
    }

    @Test
    void previewReturnsProtectedStatusAndMaskedPayload() {
        settings(true, true);
        when(currentUserService.require()).thenReturn(user);

        PayloadPreviewResponse response = service.preview(new PayloadPreviewRequest(
                "SELECT * FROM customers WHERE email='john@example.com'", null, null));

        assertThat(response.privacyStatus()).isEqualTo("PROTECTED");
        assertThat(response.sanitized().sql()).doesNotContain("john@example.com");
        assertThat(response.original().sql()).contains("john@example.com");
        assertThat(response.validation().passed()).isTrue();
        verify(auditService).record(any(), any(), isNull(), any(), anyInt(), anyLong(), any());
    }
}
