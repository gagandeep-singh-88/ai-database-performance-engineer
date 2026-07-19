package com.dbperf.report.dto;

import com.dbperf.ai.AiReportSummary;
import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;

import java.time.Instant;
import java.util.List;

/** Everything the HTML renderer needs to lay out one report. */
public record ReportModel(
        String connectionName,
        String databaseName,
        String host,
        Instant generatedAt,
        String aiModel,
        HealthAssessment health,
        SnapshotDetailResponse latest,
        List<SnapshotSummaryResponse> history,
        List<AnalysisHistoryItem> recentAnalyses,
        AiReportSummary summary) {
}
