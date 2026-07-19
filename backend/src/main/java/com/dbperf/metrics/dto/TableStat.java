package com.dbperf.metrics.dto;

/**
 * Per-table access pattern from pg_stat_user_tables. High seqTupRead with
 * few idxScans on a large table is the classic missing-index signal.
 */
public record TableStat(
        String tableName,
        long seqScans,
        long seqTupRead,
        long idxScans,
        long liveRows) {
}
