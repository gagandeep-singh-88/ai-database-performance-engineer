package com.dbperf.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user privacy controls surfaced on the Privacy page. One row per user;
 * absence implies the safe defaults (sanitization on, AI on).
 */
@Entity
@Table(name = "privacy_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** When true, SQL/plan/metrics are redacted before the AI call. */
    @Column(name = "sql_sanitization_enabled", nullable = false)
    @Builder.Default
    private boolean sqlSanitizationEnabled = true;

    /** When false, no payload is ever sent to the AI provider. */
    @Column(name = "ai_enabled", nullable = false)
    @Builder.Default
    private boolean aiEnabled = true;

    /** When false, the post-redaction residual-PII re-scan is skipped entirely. */
    @Column(name = "payload_validation_enabled", nullable = false)
    @Builder.Default
    private boolean payloadValidationEnabled = true;

    /** Whether the Query Analyzer's "preview sanitization" panel is shown to this user. */
    @Column(name = "show_payload_preview", nullable = false)
    @Builder.Default
    private boolean showPayloadPreview = true;

    /** When validation is enabled and PII is still found, block (true) vs allow-with-warning (false). */
    @Column(name = "block_on_pii_detected", nullable = false)
    @Builder.Default
    private boolean blockOnPiiDetected = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanitization_mode", nullable = false)
    @Builder.Default
    private SanitizationMode sanitizationMode = SanitizationMode.AUTOMATIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_response_style", nullable = false)
    @Builder.Default
    private AiResponseStyle aiResponseStyle = AiResponseStyle.TECHNICAL;

    /** Soft cap the AI is instructed to target for response length (characters). */
    @Column(name = "max_response_length", nullable = false)
    @Builder.Default
    private int maxResponseLength = 1000;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
