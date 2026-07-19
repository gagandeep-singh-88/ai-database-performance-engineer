package com.dbperf.metrics.dto;

/** One row from pg_stat_statements, ordered by total execution time. */
public record QueryStat(
        String queryId,
        String query,
        long calls,
        double totalTimeMs,
        double meanTimeMs,
        long rows,
        long sharedBlksHit,
        long sharedBlksRead,
        double hitRatio) {
}
