package com.dbperf.privacy.dto;

import com.dbperf.privacy.domain.PiiType;

import java.util.List;
import java.util.Map;

/**
 * Result of sanitizing a metrics JSON document.
 *
 * @param json          sanitized JSON (allow-listed metric fields only)
 * @param removedFields keys dropped because they were not on the metrics allow-list
 * @param findings      PII categories masked within retained string values
 */
public record MetricsSanitizationResult(String json, List<RemovedField> removedFields,
                                        Map<PiiType, Integer> findings) {
}
