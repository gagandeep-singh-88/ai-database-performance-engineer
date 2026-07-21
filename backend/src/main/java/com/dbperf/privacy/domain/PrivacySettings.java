package com.dbperf.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
