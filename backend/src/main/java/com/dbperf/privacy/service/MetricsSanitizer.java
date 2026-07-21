package com.dbperf.privacy.service;

import com.dbperf.privacy.domain.PiiType;
import com.dbperf.privacy.dto.MetricsSanitizationResult;
import com.dbperf.privacy.dto.RedactionResult;
import com.dbperf.privacy.dto.RemovedField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default-deny sanitizer for metrics documents. Only performance-metric fields
 * survive; anything that could be a customer record, transaction row, business
 * value or PII is dropped and reported. The AI must only ever receive
 * aggregate performance statistics — never row-level business data.
 *
 * <p>Allow-list examples: CPU, memory, execution time, rows examined/returned,
 * buffers, locks, wait events, index usage, database size, cache hit ratio.
 */
@Slf4j
@Component
public class MetricsSanitizer {

    /** Normalized key fragments that identify a legitimate performance metric. */
    private static final Set<String> ALLOWED_FRAGMENTS = Set.of(
            "cpu", "memory", "mem", "time", "latency", "duration", "rows", "row",
            "buffer", "blks", "blk", "lock", "wait", "event", "index", "idx", "scan",
            "size", "bytes", "cache", "hit", "ratio", "sessions", "active", "idle",
            "blocked", "deadlock", "temp", "calls", "tup", "xact", "commit", "rollback",
            "width", "cost", "loops", "grade", "score", "count", "capturedat", "id");

    /** Container keys whose children are metric collections we recurse into. */
    private static final Set<String> ALLOWED_CONTAINERS = Set.of(
            "summary", "snapshot", "metrics", "topqueries", "queries", "tablestats",
            "tables", "sessions", "locks", "detail", "factors", "history");

    /** Explicit metadata keys kept verbatim (structure labels, not business data). */
    private static final Set<String> ALLOWED_EXACT = Set.of(
            "tablename", "relname", "indexname", "schemaname", "nodetype", "jointype",
            "state", "waiteventtype", "waitevent", "severity", "label");

    private final PiiDetector piiDetector;
    private final ObjectMapper objectMapper;

    public MetricsSanitizer(PiiDetector piiDetector, ObjectMapper objectMapper) {
        this.piiDetector = piiDetector;
        this.objectMapper = objectMapper;
    }

    public MetricsSanitizationResult sanitize(String metricsJson) {
        List<RemovedField> removed = new ArrayList<>();
        Map<PiiType, Integer> findings = new EnumMap<>(PiiType.class);
        if (metricsJson == null || metricsJson.isBlank()) {
            return new MetricsSanitizationResult(metricsJson, removed, findings);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(metricsJson);
        } catch (JsonProcessingException e) {
            // Not JSON — treat as free text and redact defensively
            RedactionResult redacted = piiDetector.redact(metricsJson);
            return new MetricsSanitizationResult(redacted.text(), removed, redacted.findings());
        }
        JsonNode sanitized = walk(root, "metrics", removed, findings);
        try {
            return new MetricsSanitizationResult(
                    objectMapper.writeValueAsString(sanitized), removed, findings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize sanitized metrics", e);
        }
    }

    private JsonNode walk(JsonNode node, String path, List<RemovedField> removed,
                          Map<PiiType, Integer> findings) {
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                String childPath = path + "." + key;
                if (isAllowed(key)) {
                    out.set(key, walk(entry.getValue(), childPath, removed, findings));
                } else {
                    removed.add(new RemovedField(childPath, "Rejected field",
                            "Not on the performance-metrics allow-list — dropped to avoid leaking business data", 1));
                }
            });
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                out.add(walk(node.get(i), path + "[" + i + "]", removed, findings));
            }
            return out;
        }
        if (node.isTextual()) {
            RedactionResult redacted = piiDetector.redact(node.asText());
            redacted.findings().forEach((type, count) -> findings.merge(type, count, Integer::sum));
            return objectMapper.getNodeFactory().textNode(redacted.text());
        }
        return node;
    }

    private boolean isAllowed(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ALLOWED_EXACT.contains(normalized) || ALLOWED_CONTAINERS.contains(normalized)) {
            return true;
        }
        for (String fragment : ALLOWED_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
