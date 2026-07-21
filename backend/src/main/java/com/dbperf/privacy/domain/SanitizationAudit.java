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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of one sanitization/validation pass. Records WHAT
 * kinds of sensitive data were found and the outcome — never the raw values.
 * This is the compliance trail proving PII never reached the AI.
 */
@Entity
@Table(name = "sanitization_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanitizationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Tenant partition key (here derived from the user's email domain). */
    @Column(nullable = false)
    private String tenant;

    /** The analysis this pass fed, when applicable. */
    @Column(name = "analysis_id")
    private UUID analysisId;

    /** Comma-separated PII type keys detected, e.g. "EMAIL,CREDIT_CARD". Never values. */
    @Column(name = "pii_detected", columnDefinition = "TEXT")
    private String piiDetected;

    @Column(name = "fields_removed", nullable = false)
    private int fieldsRemoved;

    @Column(name = "payload_size_bytes", nullable = false)
    private long payloadSizeBytes;

    /** PASSED, BLOCKED or AI_DISABLED. */
    @Column(name = "validation_result", nullable = false)
    private String validationResult;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
