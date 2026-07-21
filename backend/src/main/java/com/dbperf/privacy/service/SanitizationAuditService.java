package com.dbperf.privacy.service;

import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.SanitizationAudit;
import com.dbperf.privacy.dto.AuditLogItem;
import com.dbperf.privacy.dto.PiiFinding;
import com.dbperf.privacy.dto.ValidationResult;
import com.dbperf.privacy.repository.SanitizationAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes and reads the sanitization audit trail. Records only PII categories
 * and outcomes — never the raw sensitive values — satisfying "log the reason,
 * not the data".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizationAuditService {

    private final SanitizationAuditRepository auditRepository;
    private final PrivacyProperties properties;

    public void record(UUID userId, String userEmail, UUID analysisId, List<PiiFinding> findings,
                       int fieldsRemoved, long payloadSizeBytes, ValidationResult validation) {
        String outcome = outcome(validation);
        String piiTypes = findings.stream().map(f -> f.type().name()).collect(Collectors.joining(","));

        // Structured, value-free audit line (safe to ship to a log aggregator)
        log.info("Sanitization audit user={} tenant={} analysis={} pii=[{}] fieldsRemoved={} size={}B result={}",
                userId, tenant(userEmail), analysisId, piiTypes, fieldsRemoved, payloadSizeBytes, outcome);

        if (!properties.auditEnabled()) {
            return;
        }
        auditRepository.save(SanitizationAudit.builder()
                .userId(userId)
                .tenant(tenant(userEmail))
                .analysisId(analysisId)
                .piiDetected(piiTypes)
                .fieldsRemoved(fieldsRemoved)
                .payloadSizeBytes(payloadSizeBytes)
                .validationResult(outcome)
                .build());
    }

    public List<AuditLogItem> list(UUID userId, int limit) {
        return auditRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(AuditLogItem::from)
                .toList();
    }

    private static String outcome(ValidationResult validation) {
        if (!validation.aiEnabled()) {
            return "AI_DISABLED";
        }
        return validation.passed() ? "PASSED" : "BLOCKED";
    }

    /** Derive a tenant partition from the email domain (single-tenant fallback: "default"). */
    static String tenant(String email) {
        if (email == null) {
            return "default";
        }
        int at = email.indexOf('@');
        return at >= 0 && at < email.length() - 1 ? email.substring(at + 1) : "default";
    }
}
