package com.dbperf.privacy.service;

import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.common.exception.SensitiveDataException;
import com.dbperf.config.PrivacyProperties;
import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.domain.PrivacySettings;
import com.dbperf.privacy.domain.SanitizationMode;
import com.dbperf.privacy.dto.MetricsSanitizationResult;
import com.dbperf.privacy.dto.PayloadPreviewRequest;
import com.dbperf.privacy.dto.PayloadPreviewResponse;
import com.dbperf.privacy.dto.PayloadSection;
import com.dbperf.privacy.dto.PiiFinding;
import com.dbperf.privacy.dto.PlaceholderMapping;
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
import java.util.Set;

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

    // ---- AI Copilot integration ------------------------------------------

    /**
     * Sanitize and validate the AI Copilot's outgoing turn — the metrics
     * grounding context (built server-side from the user's own collected
     * data) and their free-form chat message — before every call to the AI
     * provider, mirroring {@link #enforceForAnalysis}. The grounding context
     * is redacted with numeric heuristics preserved (it carries real
     * row/latency figures, like schema context in the analyzer); the chat
     * message is redacted with the full detector since it's arbitrary
     * free text a user could paste anything into. Throws
     * {@link SensitiveDataException} if AI is disabled or sensitive data
     * survives sanitization — callers must persist and send only the
     * returned sanitized text, never the raw input.
     */
    public CopilotPayload enforceForCopilot(User user, String groundingContext, String userMessage) {
        PrivacySettings settings = settingsService.resolve(user.getId());
        boolean redact = properties.enabled() && settings.isSqlSanitizationEnabled();
        PlaceholderAllocator allocator = new PlaceholderAllocator();
        List<Map<PiiType, Integer>> findingMaps = new ArrayList<>();

        String outContext = groundingContext;
        if (notBlank(groundingContext) && redact) {
            RedactionResult r = piiDetector.redact(groundingContext, PiiDetector.NUMERIC_HEURISTICS, allocator);
            outContext = r.text();
            findingMaps.add(r.findings());
        }

        String outMessage = userMessage;
        if (notBlank(userMessage) && redact) {
            RedactionResult r = piiDetector.redact(userMessage, Set.of(), allocator);
            outMessage = r.text();
            findingMaps.add(r.findings());
        }

        List<PiiFinding> findings = SanitizedPayload.mergeFindings(findingMaps);
        String combined = combined(outContext, outMessage, null);
        ValidationResult validation = payloadValidator.validate(combined, settings.isAiEnabled(),
                settings.isPayloadValidationEnabled(), settings.isBlockOnPiiDetected());
        validation = applyStrictMode(validation, settings, userMessage);

        long size = combined.getBytes(StandardCharsets.UTF_8).length;
        int fieldsRemoved = findings.stream().mapToInt(PiiFinding::occurrences).sum();
        auditService.record(user.getId(), user.getEmail(), null, findings, fieldsRemoved, size, validation);

        if (!validation.passed()) {
            throw new SensitiveDataException(blockMessage(validation));
        }
        return new CopilotPayload(outContext, outMessage);
    }

    public record CopilotPayload(String groundingContext, String userMessage) {
    }

    // ---- Privacy page preview -------------------------------------------

    public PayloadPreviewResponse preview(PayloadPreviewRequest request) {
        if (request == null || request.isEmpty()) {
            throw new InvalidRequestException("Provide SQL, an execution plan, or metrics JSON to preview");
        }
        User user = currentUserService.require();
        PrivacySettings settings = settingsService.resolve(user.getId());
        boolean redact = properties.enabled() && settings.isSqlSanitizationEnabled();
        PlaceholderAllocator allocator = new PlaceholderAllocator();

        List<Map<PiiType, Integer>> findingMaps = new ArrayList<>();
        List<RemovedField> metricsDropped = new ArrayList<>();

        String sanitizedSql = request.sql();
        if (notBlank(request.sql()) && redact) {
            RedactionResult r = sqlSanitizer.sanitize(request.sql(), allocator);
            sanitizedSql = r.text();
            findingMaps.add(r.findings());
        }

        String sanitizedPlan = request.executionPlan();
        if (notBlank(request.executionPlan()) && redact) {
            RedactionResult r = planSanitizer.sanitize(request.executionPlan(), allocator);
            sanitizedPlan = r.text();
            findingMaps.add(r.findings());
        }

        String sanitizedMetrics = request.metricsJson();
        if (notBlank(request.metricsJson()) && redact) {
            MetricsSanitizationResult r = metricsSanitizer.sanitize(request.metricsJson(), allocator);
            sanitizedMetrics = r.json();
            findingMaps.add(r.findings());
            metricsDropped.addAll(r.removedFields());
        }

        List<PiiFinding> findings = SanitizedPayload.mergeFindings(findingMaps);
        List<PlaceholderMapping> placeholders = placeholderLegend(allocator);
        List<RemovedField> removed = nonPlaceholderRemovals(findings, metricsDropped);

        String combined = combined(sanitizedSql, sanitizedPlan, sanitizedMetrics);
        ValidationResult validation = payloadValidator.validate(combined, settings.isAiEnabled(),
                settings.isPayloadValidationEnabled(), settings.isBlockOnPiiDetected());
        validation = applyStrictMode(validation, settings, request.sql());

        long size = combined.getBytes(StandardCharsets.UTF_8).length;
        int fieldsRemoved = findings.stream().mapToInt(PiiFinding::occurrences).sum();
        auditService.record(user.getId(), user.getEmail(), null, findings, fieldsRemoved, size, validation);

        return new PayloadPreviewResponse(
                new PayloadSection(request.sql(), request.executionPlan(), request.metricsJson()),
                new PayloadSection(sanitizedSql, sanitizedPlan, sanitizedMetrics),
                findings, placeholders, removed, validation, status(validation));
    }

    // ---- shared core ----------------------------------------------------

    /** Pure sanitize + validate with no persistence; reused by both entry points. */
    SanitizedPayload sanitize(String sql, String plan, String schemaContext, PrivacySettings settings) {
        boolean redact = properties.enabled() && settings.isSqlSanitizationEnabled();
        PlaceholderAllocator allocator = new PlaceholderAllocator();
        List<Map<PiiType, Integer>> findingMaps = new ArrayList<>();

        String outSql = sql;
        if (notBlank(sql) && redact) {
            RedactionResult r = sqlSanitizer.sanitize(sql, allocator);
            outSql = r.text();
            findingMaps.add(r.findings());
        }

        String outPlan = plan;
        if (notBlank(plan) && redact) {
            RedactionResult r = planSanitizer.sanitize(plan, allocator);
            outPlan = r.text();
            findingMaps.add(r.findings());
        }

        String outSchema = schemaContext;
        if (notBlank(schemaContext) && redact) {
            // Schema/statistics context carries row counts — preserve numeric metrics.
            RedactionResult r = piiDetector.redact(schemaContext, PiiDetector.NUMERIC_HEURISTICS, allocator);
            outSchema = r.text();
            findingMaps.add(r.findings());
        }

        List<PiiFinding> findings = SanitizedPayload.mergeFindings(findingMaps);
        List<PlaceholderMapping> placeholders = placeholderLegend(allocator);
        List<RemovedField> removed = nonPlaceholderRemovals(findings, List.of());
        String combined = combined(outSql, outPlan, outSchema);
        ValidationResult validation = payloadValidator.validate(combined, settings.isAiEnabled(),
                settings.isPayloadValidationEnabled(), settings.isBlockOnPiiDetected());
        validation = applyStrictMode(validation, settings, sql);
        return new SanitizedPayload(outSql, outPlan, outSchema, findings, placeholders, removed, validation);
    }

    /**
     * Strict Block mode rejects a request outright if the RAW SQL contains any
     * detectable PII, regardless of whether sanitization would otherwise have
     * cleaned it up. Only SQL is scanned raw (not plan/schema context), since
     * their numeric heuristics would false-positive on cost/row estimates.
     */
    private ValidationResult applyStrictMode(ValidationResult validation, PrivacySettings settings, String rawSql) {
        if (validation.aiEnabled() && settings.getSanitizationMode() == SanitizationMode.STRICT_BLOCK
                && notBlank(rawSql) && piiDetector.containsSensitiveData(rawSql)) {
            return ValidationResult.blockedByStrictMode();
        }
        return validation;
    }

    private static List<PlaceholderMapping> placeholderLegend(PlaceholderAllocator allocator) {
        return allocator.entries().stream().map(PlaceholderMapping::from).toList();
    }

    /** Removals that have no placeholder: stripped comments and dropped metric keys. */
    private static List<RemovedField> nonPlaceholderRemovals(List<PiiFinding> findings,
                                                             List<RemovedField> metricsDropped) {
        List<RemovedField> out = new ArrayList<>();
        findings.stream()
                .filter(f -> f.type() == PiiType.SQL_COMMENT)
                .findFirst()
                .ifPresent(f -> out.add(new RemovedField("SQL / plan", "SQL comment",
                        "Comment removed entirely — comments can carry names or business context",
                        f.occurrences())));
        out.addAll(metricsDropped);
        return out;
    }

    private static String blockMessage(ValidationResult validation) {
        if (!validation.aiEnabled()) {
            return "AI is disabled in your privacy settings, so this request was not sent to the AI.";
        }
        if (validation.residualFindings().isEmpty()) {
            return validation.message();
        }
        String types = validation.residualFindings().stream().map(PiiFinding::label).distinct().toList().toString();
        return "Request blocked: sensitive data " + types + " was detected and could not be fully "
                + "sanitized. Nothing was sent to the AI. Remove the sensitive values and try again.";
    }

    private static String status(ValidationResult validation) {
        if (!validation.aiEnabled()) {
            return "AI_DISABLED";
        }
        if (!validation.passed()) {
            return "BLOCKED";
        }
        return validation.residualFindings().isEmpty() ? "PROTECTED" : "ALLOWED_WITH_WARNING";
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
