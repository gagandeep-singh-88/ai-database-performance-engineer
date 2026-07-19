package com.dbperf.dashboard.dto;

import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;

import java.util.List;

/**
 * Everything the dashboard needs in one round trip.
 * hasData=false means no snapshots exist yet for the connection.
 */
public record DashboardResponse(
        boolean hasData,
        int healthScore,
        String grade,
        List<ScoreFactor> factors,
        List<Recommendation> recommendations,
        SnapshotDetailResponse latest,
        List<SnapshotSummaryResponse> history) {

    public static DashboardResponse empty() {
        return new DashboardResponse(false, 0, "—", List.of(), List.of(), null, List.of());
    }

    /** A deduction applied to the health score, e.g. "2 sequential-scan hotspots (-15)". */
    public record ScoreFactor(String label, int penalty) {
    }

    public record Recommendation(String severity, String title, String detail, String sql) {
    }
}
