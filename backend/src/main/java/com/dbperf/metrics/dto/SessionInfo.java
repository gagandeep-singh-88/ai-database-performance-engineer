package com.dbperf.metrics.dto;

/** One client backend from pg_stat_activity. */
public record SessionInfo(
        int pid,
        String user,
        String state,
        String waitEventType,
        String waitEvent,
        Long durationSeconds,
        String query) {
}
