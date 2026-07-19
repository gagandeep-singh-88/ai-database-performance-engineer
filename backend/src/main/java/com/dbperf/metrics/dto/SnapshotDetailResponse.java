package com.dbperf.metrics.dto;

import java.util.List;

/** Full snapshot including drill-down details. */
public record SnapshotDetailResponse(
        SnapshotSummaryResponse summary,
        List<QueryStat> topQueries,
        List<SessionInfo> sessions,
        List<LockInfo> locks,
        List<TableStat> tableStats) {
}
