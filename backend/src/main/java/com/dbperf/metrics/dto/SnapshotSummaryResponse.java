package com.dbperf.metrics.dto;

import com.dbperf.metrics.domain.MetricSnapshot;

import java.time.Instant;
import java.util.UUID;

/** Lightweight snapshot row for history lists and charts — no JSON blobs. */
public record SnapshotSummaryResponse(
        UUID id,
        UUID connectionId,
        Instant capturedAt,
        long dbSizeBytes,
        int activeSessions,
        int idleInTransaction,
        int blockedSessions,
        int waitingLocks,
        Double cacheHitRatio,
        long xactCommit,
        long xactRollback,
        long deadlocks,
        long tempBytes) {

    public static SnapshotSummaryResponse from(MetricSnapshot snapshot) {
        return new SnapshotSummaryResponse(
                snapshot.getId(),
                snapshot.getConnectionId(),
                snapshot.getCapturedAt(),
                snapshot.getDbSizeBytes(),
                snapshot.getActiveSessions(),
                snapshot.getIdleInTransaction(),
                snapshot.getBlockedSessions(),
                snapshot.getWaitingLocks(),
                snapshot.getCacheHitRatio(),
                snapshot.getXactCommit(),
                snapshot.getXactRollback(),
                snapshot.getDeadlocks(),
                snapshot.getTempBytes());
    }
}
