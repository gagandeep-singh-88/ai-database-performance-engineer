package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.SanitizationAudit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A read-only audit row for the Privacy page. Exposes only types and counts.
 */
public record AuditLogItem(
        UUID id,
        Instant timestamp,
        String tenant,
        UUID analysisId,
        List<String> piiDetected,
        int fieldsRemoved,
        long payloadSizeBytes,
        String validationResult) {

    public static AuditLogItem from(SanitizationAudit audit) {
        String detected = audit.getPiiDetected();
        List<String> pii = (detected == null || detected.isBlank())
                ? List.of()
                : List.of(detected.split(","));
        return new AuditLogItem(audit.getId(), audit.getCreatedAt(), audit.getTenant(),
                audit.getAnalysisId(), pii, audit.getFieldsRemoved(),
                audit.getPayloadSizeBytes(), audit.getValidationResult());
    }
}
