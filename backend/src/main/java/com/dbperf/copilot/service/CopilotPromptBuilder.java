package com.dbperf.copilot.service;

import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.dashboard.dto.DashboardResponse.ScoreFactor;
import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the copilot prompts. The system prompt is stable (persona + rules);
 * the per-conversation grounding context — health score, latest snapshot,
 * recent analyses — is injected as the first user-visible context block.
 */
@Component
public class CopilotPromptBuilder {

    static final String SYSTEM_PROMPT = """
            You are DBPerfAI Copilot, a senior PostgreSQL database performance engineer embedded \
            in a monitoring tool. The user is looking at metrics collected from their own database, \
            and you have been given the latest collected data as context.

            Rules:
            - Ground every answer in the provided metrics context: reference actual table names, \
            query texts, timings, and counts. Never give generic advice when specifics are available.
            - If the question cannot be answered from the context (no snapshot yet, no connection \
            selected, or data simply not collected), say so plainly and tell the user what to do \
            (e.g. add a connection, wait for the collector, or use the Query Analyzer for a specific SQL).
            - Any SQL you suggest must be ready to run; use CREATE INDEX CONCURRENTLY for indexes. \
            Warn before anything that takes heavy locks or rewrites tables.
            - Be honest about estimates and uncertainty. If the metrics look healthy, say so — \
            do not invent problems.
            - Keep replies focused and readable: short paragraphs, bullets for lists, ```sql fences \
            for SQL. Answer the question asked before adding extra observations.""";

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** No-connection variant so the copilot can still explain the product and general tuning. */
    public String ungroundedContext() {
        return """
                ## Metrics context
                No database connection is selected for this conversation, so no live metrics are \
                available. You can still answer general PostgreSQL performance questions and explain \
                how DBPerfAI works (connections, collector snapshots every 5 minutes, health dashboard, \
                query analyzer).""";
    }

    public String groundingContext(DatabaseConnection connection,
                                   HealthAssessment health,
                                   SnapshotDetailResponse latest,
                                   List<AnalysisHistoryItem> recentAnalyses) {
        SnapshotSummaryResponse s = latest.summary();
        StringBuilder context = new StringBuilder();
        context.append("## Metrics context: %s (database \"%s\"), snapshot captured %s\n"
                .formatted(connection.getName(), connection.getDatabaseName(), s.capturedAt()));

        context.append("\n### Health score: %d/100 (grade %s)\n".formatted(health.score(), health.grade()));
        for (ScoreFactor factor : health.factors()) {
            context.append("- -%d points: %s\n".formatted(factor.penalty(), factor.label()));
        }
        if (health.factors().isEmpty()) {
            context.append("- No deductions; all monitored signals healthy\n");
        }

        context.append("\n### Snapshot summary\n");
        context.append("- Size: %.0f MB, cache hit ratio: %s, active sessions: %d, idle-in-txn: %d, blocked: %d, waiting locks: %d, deadlocks: %d, temp bytes: %d\n"
                .formatted(s.dbSizeBytes() / 1e6,
                        s.cacheHitRatio() == null ? "n/a" : "%.1f%%".formatted(s.cacheHitRatio() * 100),
                        s.activeSessions(), s.idleInTransaction(), s.blockedSessions(),
                        s.waitingLocks(), s.deadlocks(), s.tempBytes()));

        context.append("\n### Top queries by total time (pg_stat_statements)\n");
        for (QueryStat q : latest.topQueries().stream().limit(12).toList()) {
            context.append("- calls=%d mean=%.1fms total=%.0fms cacheHit=%.0f%%: %s\n"
                    .formatted(q.calls(), q.meanTimeMs(), q.totalTimeMs(), q.hitRatio() * 100,
                            truncate(q.query().replaceAll("\\s+", " "), 220)));
        }

        context.append("\n### Table access patterns (pg_stat_user_tables)\n");
        for (TableStat t : latest.tableStats()) {
            context.append("- %s: ~%,d rows, seq_scans=%d (rows read seq: %,d), idx_scans=%d\n"
                    .formatted(t.tableName(), t.liveRows(), t.seqScans(), t.seqTupRead(), t.idxScans()));
        }

        if (!recentAnalyses.isEmpty()) {
            context.append("\n### Recent Query Analyzer results for this user\n");
            for (AnalysisHistoryItem item : recentAnalyses) {
                context.append("- [%s] %s\n".formatted(item.createdAt(),
                        truncate(item.summary() == null ? "(no summary)" : item.summary(), 200)));
            }
        }

        return context.toString();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
