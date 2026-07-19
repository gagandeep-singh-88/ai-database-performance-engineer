package com.dbperf.report.service;

import com.dbperf.ai.AiReportSummary;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.dashboard.service.HealthScoreCalculator;
import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import com.dbperf.report.dto.ReportModel;
import com.dbperf.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    static final String REPORT_SYSTEM_PROMPT = """
            You are a senior PostgreSQL performance engineer writing the narrative sections of a \
            formal optimization report for an engineering lead. You are given a health assessment \
            and the latest collected metrics. Write an honest executive summary, concrete key \
            findings, and a prioritized action plan. Reference actual tables, queries and numbers \
            from the data — never generic advice. Where directly actionable, include ready-to-run \
            SQL (CREATE INDEX CONCURRENTLY for indexes). If the database is healthy, say so \
            plainly and keep the plan short — do not invent problems.""";

    private final QueryAnalysisAi ai;
    private final ConnectionAccess connectionAccess;
    private final MetricSnapshotRepository snapshotRepository;
    private final MetricsCollectorService collectorService;
    private final HealthScoreCalculator healthScoreCalculator;
    private final QueryAnalysisRepository analysisRepository;
    private final CurrentUserService currentUserService;
    private final ReportHtmlRenderer htmlRenderer;
    private final PdfRenderer pdfRenderer;

    public record GeneratedReport(byte[] pdf, String filename) {
    }

    public GeneratedReport generate(UUID connectionId) {
        DatabaseConnection connection = connectionAccess.requireOwned(connectionId);
        SnapshotDetailResponse latest = snapshotRepository
                .findFirstByConnectionIdOrderByCapturedAtDesc(connectionId)
                .map(collectorService::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No snapshots collected yet for this connection — the report needs metrics first"));

        HealthAssessment health = healthScoreCalculator.assess(latest);
        List<SnapshotSummaryResponse> history = snapshotRepository
                .findAllByConnectionIdOrderByCapturedAtDesc(connectionId, PageRequest.of(0, 24))
                .stream()
                .map(SnapshotSummaryResponse::from)
                .toList();
        List<AnalysisHistoryItem> recentAnalyses = analysisRepository
                .findAllByUserIdOrderByCreatedAtDesc(currentUserService.require().getId(), PageRequest.of(0, 5))
                .stream()
                .map(AnalysisHistoryItem::from)
                .toList();

        long start = System.currentTimeMillis();
        AiReportSummary summary = ai.structured(REPORT_SYSTEM_PROMPT,
                reportPrompt(connection, health, latest), AiReportSummary.class);

        ReportModel model = new ReportModel(connection.getName(), connection.getDatabaseName(),
                connection.getHost(), Instant.now(), ai.model(), health, latest, history,
                recentAnalyses, summary);
        byte[] pdf = pdfRenderer.toPdf(htmlRenderer.render(model));
        log.info("Report for {} generated in {} ms ({} bytes)",
                connection.getName(), System.currentTimeMillis() - start, pdf.length);

        return new GeneratedReport(pdf, filename(connection));
    }

    static String reportPrompt(DatabaseConnection connection, HealthAssessment health,
                               SnapshotDetailResponse latest) {
        SnapshotSummaryResponse s = latest.summary();
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Database: %s (\"%s\"), snapshot captured %s\n".formatted(
                connection.getName(), connection.getDatabaseName(), s.capturedAt()));
        prompt.append("Health score: %d/100 (grade %s)\n".formatted(health.score(), health.grade()));
        health.factors().forEach(factor ->
                prompt.append("- -%d points: %s\n".formatted(factor.penalty(), factor.label())));

        prompt.append("\n## Snapshot summary\n");
        prompt.append("- Size: %.0f MB, cache hit ratio: %s, active sessions: %d, idle-in-txn: %d, blocked: %d, waiting locks: %d, deadlocks: %d, temp bytes: %d\n"
                .formatted(s.dbSizeBytes() / 1e6,
                        s.cacheHitRatio() == null ? "n/a" : "%.1f%%".formatted(s.cacheHitRatio() * 100),
                        s.activeSessions(), s.idleInTransaction(), s.blockedSessions(),
                        s.waitingLocks(), s.deadlocks(), s.tempBytes()));

        prompt.append("\n## Top queries by total time (pg_stat_statements)\n");
        for (QueryStat q : latest.topQueries().stream().limit(12).toList()) {
            prompt.append("- calls=%d mean=%.1fms total=%.0fms cacheHit=%.0f%%: %s\n"
                    .formatted(q.calls(), q.meanTimeMs(), q.totalTimeMs(), q.hitRatio() * 100,
                            q.query().replaceAll("\\s+", " ")
                                    .substring(0, Math.min(220, q.query().replaceAll("\\s+", " ").length()))));
        }

        prompt.append("\n## Table access patterns (pg_stat_user_tables)\n");
        for (TableStat t : latest.tableStats()) {
            prompt.append("- %s: ~%,d rows, seq_scans=%d (rows read seq: %,d), idx_scans=%d\n"
                    .formatted(t.tableName(), t.liveRows(), t.seqScans(), t.seqTupRead(), t.idxScans()));
        }

        prompt.append("\nWrite the report sections.");
        return prompt.toString();
    }

    private static String filename(DatabaseConnection connection) {
        String safeName = connection.getName().replaceAll("[^A-Za-z0-9-]+", "-")
                .replaceAll("(^-|-$)", "").toLowerCase();
        return "dbperf-report-%s-%s.pdf".formatted(
                safeName.isBlank() ? "database" : safeName,
                java.time.LocalDate.now(java.time.ZoneOffset.UTC));
    }
}
