package com.dbperf.privacy.service;

import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.common.exception.SensitiveDataException;
import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.domain.PrivacySettings;
import com.dbperf.privacy.dto.MetricsSanitizationResult;
import com.dbperf.privacy.dto.PayloadPreviewRequest;
import com.dbperf.privacy.dto.PayloadPreviewResponse;
import com.dbperf.privacy.dto.PayloadSection;
import com.dbperf.privacy.dto.PiiFinding;
import com.dbperf.privacy.dto.RedactionResult;
import com.dbperf.privacy.dto.RemovedField;
import com.dbperf.privacy.dto.SanitizedPayload;
import com.dbperf.privacy.dto.ValidationResult;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Privacy &amp; Sanitization Engine — the mandatory gate
 * between the analysis engine and the AI provider.
 *
 * <pre>
 *   Query Analyzer / Metrics
 *          │  raw SQL, execution plan, schema stats
 *          ▼
 *   SanitizationService.enforceForAnalysis()
 *          │  1. redact (SQL / plan / schema)      2. validate residue
 *          │  3. audit (types + outcome, no values)
 *          ▼
 *   Sanitized payload  ─►  Claude API
 * </pre>
 *
 * Two entry points share the same core: {@link #enforceForAnalysis} (used by
 * the Query Analyzer — throws if blocked) and {@link #preview} (used by the
 * Privacy page — never throws, returns the blocked state for display).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizationService {

    private final SqlSanitizer sqlSanitizer;
    private final ExecutionPlanSanitizer planSanitizer;
    private final MetricsSanitizer metricsSanitizer;
    private final PayloadValidator payloadValidator;
    private final PiiDetector piiDetector;
    private final PrivacySettingsService settingsService;
    private final SanitizationAuditService auditService;
    private final CurrentUserService currentUserService;
    private final PrivacyProperties properties;

    // ---- Query Analyzer integration -------------------------------------

    /**
     * Sanitize and validate an analysis payload, audit the pass, and return the
     * safe text to forward to the AI. Throws {@link SensitiveDataException} if
     * AI is disabled or sensitive data survives sanitization.
     */
    public SanitizedPayload enforceForAnalysis(User user, java.util.UUID analysisId,
                                               String sql, String plan, String schemaContext) {
        PrivacySettings settings = settingsService.resolve(user.getId());
        SanitizedPayload payload = sanitize(sql, plan, schemaContext, settings);

        String combined = combined(payload.sql(), payload.plan(), payload.schemaContext());
        long size = combined.getBytes(StandardCharsets.UTF_8).length;
        int fieldsRemoved = payload.findings().stream().mapToInt(PiiFinding::occurrences).sum();
        auditService.record(user.getId(), user.getEmail(), analysisId, payload.findings(),
                fieldsRemoved, size, payload.validation());

        if (!payload.validation().passed()) {
            throw new SensitiveDataException(blockMessage(payload.validation()));
        }
        return payload;
    }

    // ---- Privacy page preview -------------------------------------------

    public PayloadPreviewResponse preview(PayloadPreviewRequest request) {
        if (request == null || request.isEmpty()) {
            throw new InvalidRequestException("Provide SQL, an execution plan, or metrics JSON to preview");
        }
        User user = currentUserService.require();
        PrivacySettings settings = settingsService.resolve(user.getId());
        boolean redact = properties.enabled() && settings.isSqlSanitizationEnabled();

        List<Map<PiiType, Integer>> findingMaps = new ArrayList<>();
        List<RemovedField> removed = new ArrayList<>();

        String sanitizedSql = request.sql();
        if (notBlank(request.sql())) {
            if (redact) {
                RedactionResult r = sqlSanitizer.sanitize(request.sql());
                sanitizedSql = r.text();
                findingMaps.add(r.findings());
                removed.addAll(describe("SQL", r.findings()));
            }
        }

        String sanitizedPlan = request.executionPlan();
        if (notBlank(request.executionPlan())) {
            if (redact) {
                RedactionResult r = planSanitizer.sanitize(request.executionPlan());
                sanitizedPlan = r.text();
                findingMaps.add(r.findings());
                removed.addAll(describe("Execution plan", r.findings()));
            }
        }

        String sanitizedMetrics = request.metricsJson();
        if (notBlank(request.metricsJson())) {
            if (redact) {
                MetricsSanitizationResult r = metricsSanitizer.sanitize(request.metricsJson());
                sanitizedMetrics = r.json();
                findingMaps.add(r.findings());
                removed.addAll(r.removedFields());
                removed.addAll(describe("metrics", r.findings()));
            }
        }

        List<PiiFinding> findings = SanitizedPayload.mergeFindings(findingMaps);
        String combined = combined(sanitizedSql, sanitizedPlan, sanitizedMetrics);
        ValidationResult validation = payloadValidator.validate(combined, settings.isAiEnabled());

        long size = combined.getBytes(StandardCharsets.UTF_8).length;
        int fieldsRemoved = findings.stream().mapToInt(PiiFinding::occurrences).sum() + removed.size();
        auditService.record(user.getId(), user.getEmail(), null, findings, fieldsRemoved, size, validation);

        return new PayloadPreviewResponse(
                new PayloadSection(request.sql(), request.executionPlan(), request.metricsJson()),
                new PayloadSection(sanitizedSql, sanitizedPlan, sanitizedMetrics),
                findings, removed, validation, status(validation));
    }

    // ---- shared core ----------------------------------------------------

    /** Pure sanitize + validate with no persistence; reused by both entry points. */
    SanitizedPayload sanitize(String sql, String plan, String schemaContext, PrivacySettings settings) {
        boolean redact = properties.enabled() && settings.isSqlSanitizationEnabled();
        List<Map<PiiType, Integer>> findingMaps = new ArrayList<>();
        List<RemovedField> removed = new ArrayList<>();

        String outSql = sql;
        if (notBlank(sql) && redact) {
            RedactionResult r = sqlSanitizer.sanitize(sql);
            outSql = r.text();
            findingMaps.add(r.findings());
            removed.addAll(describe("SQL", r.findings()));
        }

        String outPlan = plan;
        if (notBlank(plan) && redact) {
            RedactionResult r = planSanitizer.sanitize(plan);
            outPlan = r.text();
            findingMaps.add(r.findings());
            removed.addAll(describe("Execution plan", r.findings()));
        }

        String outSchema = schemaContext;
        if (notBlank(schemaContext) && redact) {
            // Schema/statistics context carries row counts — preserve numeric metrics.
            RedactionResult r = piiDetector.redact(schemaContext, PiiDetector.NUMERIC_HEURISTICS);
            outSchema = r.text();
            findingMaps.add(r.findings());
            removed.addAll(describe("Schema context", r.findings()));
        }

        List<PiiFinding> findings = SanitizedPayload.mergeFindings(findingMaps);
        String combined = combined(outSql, outPlan, outSchema);
        ValidationResult validation = payloadValidator.validate(combined, settings.isAiEnabled());
        return new SanitizedPayload(outSql, outPlan, outSchema, findings, removed, validation);
    }

    private static List<RemovedField> describe(String location, Map<PiiType, Integer> findings) {
        List<RemovedField> out = new ArrayList<>();
        findings.forEach((type, count) -> out.add(new RemovedField(location, type.label(),
                "Masked before sending to the AI to protect sensitive data", count)));
        return out;
    }

    private static String blockMessage(ValidationResult validation) {
        if (!validation.aiEnabled()) {
            return "AI is disabled in your privacy settings, so this request was not sent to the AI.";
        }
        String types = validation.residualFindings().stream().map(PiiFinding::label).distinct().toList().toString();
        return "Request blocked: sensitive data " + types + " was detected and could not be fully "
                + "sanitized. Nothing was sent to the AI. Remove the sensitive values and try again.";
    }

    private static String status(ValidationResult validation) {
        if (!validation.aiEnabled()) {
            return "AI_DISABLED";
        }
        return validation.passed() ? "PROTECTED" : "BLOCKED";
    }

    private static String combined(String sql, String plan, String extra) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(sql)) {
            sb.append(sql).append('\n');
        }
        if (notBlank(plan)) {
            sb.append(plan).append('\n');
        }
        if (notBlank(extra)) {
            sb.append(extra).append('\n');
        }
        return sb.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
