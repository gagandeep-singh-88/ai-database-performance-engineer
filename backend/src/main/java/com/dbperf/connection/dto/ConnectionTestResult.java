package com.dbperf.connection.dto;

/**
 * Outcome of probing a target database. Doubles as capability discovery:
 * pgStatStatementsEnabled tells the Performance Collector (Module 3)
 * whether query statistics are available on this target.
 */
public record ConnectionTestResult(
        boolean success,
        long latencyMs,
        String serverVersion,
        String databaseSize,
        boolean pgStatStatementsEnabled,
        boolean readOnlyEnforced,
        String message) {

    public static ConnectionTestResult failure(long latencyMs, String message) {
        return new ConnectionTestResult(false, latencyMs, null, null, false, false, message);
    }
}
