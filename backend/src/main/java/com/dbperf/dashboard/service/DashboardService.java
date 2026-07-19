package com.dbperf.dashboard.service;

import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.dashboard.dto.DashboardResponse;
import com.dbperf.dashboard.dto.DashboardResponse.Recommendation;
import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String AI_RECS_SYSTEM_PROMPT = """
            You are a senior PostgreSQL performance engineer reviewing a health snapshot of a \
            production database. Produce 3-6 prioritized, concrete recommendations. Reference the \
            actual tables, queries and numbers from the snapshot — no generic advice. Where a \
            recommendation is directly actionable with SQL (index creation, configuration), include \
            ready-to-run SQL (CREATE INDEX CONCURRENTLY for indexes). Severity: HIGH/MEDIUM/LOW.""";

    private final ConnectionAccess connectionAccess;
    private final MetricSnapshotRepository snapshotRepository;
    private final MetricsCollectorService collectorService;
    private final HealthScoreCalculator healthScoreCalculator;
    private final QueryAnalysisAi ai;

    public DashboardResponse dashboard(UUID connectionId, int historyLimit) {
        connectionAccess.requireOwned(connectionId);
        return snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId)
                .map(snapshot -> {
                    SnapshotDetailResponse latest = collectorService.toDetail(snapshot);
                    HealthAssessment assessment = healthScoreCalculator.assess(latest);
                    List<SnapshotSummaryResponse> history = snapshotRepository
                            .findAllByConnectionIdOrderByCapturedAtDesc(connectionId,
                                    PageRequest.of(0, Math.min(historyLimit, 500)))
                            .stream()
                            .map(SnapshotSummaryResponse::from)
                            .toList();
                    return new DashboardResponse(true, assessment.score(), assessment.grade(),
                            assessment.factors(), assessment.recommendations(), latest, history);
                })
                .orElse(DashboardResponse.empty());
    }

    /** Claude-generated recommendations grounded in the latest snapshot. */
    public List<Recommendation> aiRecommendations(UUID connectionId) {
        DatabaseConnection connection = connectionAccess.requireOwned(connectionId);
        SnapshotDetailResponse latest = snapshotRepository
                .findFirstByConnectionIdOrderByCapturedAtDesc(connectionId)
                .map(collectorService::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("No snapshots collected yet for this connection"));

        AiRecommendations result = ai.structured(AI_RECS_SYSTEM_PROMPT,
                snapshotPrompt(connection, latest), AiRecommendations.class);
        return result.recommendations().stream()
                .map(item -> new Recommendation(item.severity(), item.title(), item.detail(), item.sql()))
                .toList();
    }

    static String snapshotPrompt(DatabaseConnection connection, SnapshotDetailResponse latest) {
        SnapshotSummaryResponse s = latest.summary();
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Database health snapshot (%s, %s)\n".formatted(
                connection.getName(), connection.getDatabaseName()));
        prompt.append("- Size: %.0f MB, cache hit ratio: %s, active sessions: %d, idle-in-txn: %d, blocked: %d, waiting locks: %d, deadlocks: %d, temp bytes: %d\n"
                .formatted(s.dbSizeBytes() / 1e6,
                        s.cacheHitRatio() == null ? "n/a" : "%.1f%%".formatted(s.cacheHitRatio() * 100),
                        s.activeSessions(), s.idleInTransaction(), s.blockedSessions(),
                        s.waitingLocks(), s.deadlocks(), s.tempBytes()));

        prompt.append("\n## Top queries by total time (from pg_stat_statements)\n");
        for (QueryStat q : latest.topQueries().stream().limit(10).toList()) {
            prompt.append("- calls=%d mean=%.1fms total=%.0fms cacheHit=%.0f%%: %s\n"
                    .formatted(q.calls(), q.meanTimeMs(), q.totalTimeMs(), q.hitRatio() * 100,
                            q.query().replaceAll("\\s+", " ").substring(0, Math.min(200, q.query().length()))));
        }

        prompt.append("\n## Table access patterns (pg_stat_user_tables)\n");
        for (TableStat t : latest.tableStats()) {
            prompt.append("- %s: ~%,d rows, seq_scans=%d (rows read seq: %,d), idx_scans=%d\n"
                    .formatted(t.tableName(), t.liveRows(), t.seqScans(), t.seqTupRead(), t.idxScans()));
        }

        prompt.append("\nProduce your prioritized recommendations.");
        return prompt.toString();
    }

    public record AiRecommendations(
            @JsonPropertyDescription("3-6 recommendations ordered by impact, highest first")
            List<Item> recommendations) {

        public record Item(
                @JsonPropertyDescription("One of: HIGH, MEDIUM, LOW")
                String severity,
                @JsonPropertyDescription("Short imperative title")
                String title,
                @JsonPropertyDescription("Why this matters and what to do, referencing snapshot specifics")
                String detail,
                @JsonPropertyDescription("Ready-to-run SQL if directly actionable, else null")
                String sql) {
        }
    }
}
