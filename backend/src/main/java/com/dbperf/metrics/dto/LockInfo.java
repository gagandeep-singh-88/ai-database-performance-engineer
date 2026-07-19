package com.dbperf.metrics.dto;

/** A blocked/blocking session pair derived from pg_blocking_pids(). */
public record LockInfo(
        int blockedPid,
        String blockedQuery,
        int blockingPid,
        String blockingQuery) {
}
