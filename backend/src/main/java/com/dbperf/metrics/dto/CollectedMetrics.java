package com.dbperf.metrics.dto;

import java.util.List;

/** Everything read from a target database in one collection pass. */
public record CollectedMetrics(
        long dbSizeBytes,
        int activeSessions,
        int idleInTransaction,
        int blockedSessions,
        int waitingLocks,
        Double cacheHitRatio,
        long xactCommit,
        long xactRollback,
        long deadlocks,
        long tempBytes,
        List<QueryStat> topQueries,
        List<SessionInfo> sessions,
        List<LockInfo> locks,
        List<TableStat> tableStats) {
}
