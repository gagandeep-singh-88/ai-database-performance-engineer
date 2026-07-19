package com.dbperf.dashboard.service;

import com.dbperf.dashboard.dto.DashboardResponse.Recommendation;
import com.dbperf.dashboard.dto.DashboardResponse.ScoreFactor;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, explainable health scoring: start at 100 and apply
 * documented penalties for the classic PostgreSQL failure signals.
 * Every deduction is returned as a factor so the dashboard can show
 * exactly WHY the score is what it is — no black box.
 */
@Component
public class HealthScoreCalculator {

    public record HealthAssessment(int score, String grade, List<ScoreFactor> factors,
                                   List<Recommendation> recommendations) {
    }

    public HealthAssessment assess(SnapshotDetailResponse latest) {
        SnapshotSummaryResponse s = latest.summary();
        List<ScoreFactor> factors = new ArrayList<>();
        List<Recommendation> recs = new ArrayList<>();
        int score = 100;

        // Cache efficiency
        Double cacheHit = s.cacheHitRatio();
        if (cacheHit != null && cacheHit < 0.90) {
            score -= 15;
            factors.add(new ScoreFactor("Cache hit ratio %.1f%% (< 90%%)".formatted(cacheHit * 100), 15));
            recs.add(new Recommendation("HIGH", "Improve buffer cache efficiency",
                    "Cache hit ratio is %.1f%%. Frequently read data is not fitting in shared_buffers — consider raising shared_buffers or adding indexes so queries read fewer blocks.".formatted(cacheHit * 100),
                    null));
        } else if (cacheHit != null && cacheHit < 0.95) {
            score -= 5;
            factors.add(new ScoreFactor("Cache hit ratio %.1f%% (< 95%%)".formatted(cacheHit * 100), 5));
        }

        // Concurrency problems
        if (s.blockedSessions() > 0) {
            score -= 15;
            factors.add(new ScoreFactor(s.blockedSessions() + " blocked session(s)", 15));
            recs.add(new Recommendation("HIGH", "Investigate lock contention",
                    "Sessions are currently blocked waiting on locks. Check the locking panel for the blocking PIDs and consider shorter transactions.",
                    null));
        }
        if (s.waitingLocks() > 0) {
            score -= 5;
            factors.add(new ScoreFactor(s.waitingLocks() + " ungranted lock(s)", 5));
        }
        if (s.deadlocks() > 0) {
            score -= 10;
            factors.add(new ScoreFactor(s.deadlocks() + " deadlock(s) since stats reset", 10));
            recs.add(new Recommendation("MEDIUM", "Eliminate deadlocks",
                    "Deadlocks were detected. Ensure transactions acquire locks in a consistent order.",
                    null));
        }
        if (s.idleInTransaction() > 0) {
            score -= 5;
            factors.add(new ScoreFactor(s.idleInTransaction() + " idle-in-transaction session(s)", 5));
            recs.add(new Recommendation("MEDIUM", "Close idle transactions",
                    "Idle-in-transaction sessions hold locks and block VACUUM. Set idle_in_transaction_session_timeout and audit application connection handling.",
                    "SET idle_in_transaction_session_timeout = '60s';"));
        }

        // Sequential-scan hotspots (the missing-index signal)
        List<TableStat> hotspots = latest.tableStats().stream()
                .filter(t -> t.seqTupRead() > 1_000_000 && t.idxScans() < t.seqScans())
                .toList();
        if (!hotspots.isEmpty()) {
            int penalty = Math.min(hotspots.size() * 10, 20);
            score -= penalty;
            factors.add(new ScoreFactor(hotspots.size() + " sequential-scan hotspot(s)", penalty));
            for (TableStat hotspot : hotspots) {
                recs.add(new Recommendation("HIGH",
                        "Likely missing index on \"" + hotspot.tableName() + "\"",
                        ("\"%s\" has been sequentially scanned %,d times reading %,d rows, with only %,d index scans. "
                                + "Run its slowest query through the Query Analyzer to get the exact index to create.")
                                .formatted(hotspot.tableName(), hotspot.seqScans(), hotspot.seqTupRead(), hotspot.idxScans()),
                        null));
            }
        }

        // Slow queries
        List<QueryStat> verySlow = latest.topQueries().stream().filter(q -> q.meanTimeMs() > 1000).toList();
        List<QueryStat> slow = latest.topQueries().stream()
                .filter(q -> q.meanTimeMs() > 100 && q.meanTimeMs() <= 1000).toList();
        if (!verySlow.isEmpty()) {
            int penalty = Math.min(verySlow.size() * 10, 20);
            score -= penalty;
            factors.add(new ScoreFactor(verySlow.size() + " query shape(s) averaging > 1s", penalty));
        }
        if (!slow.isEmpty()) {
            int penalty = Math.min(slow.size() * 5, 15);
            score -= penalty;
            factors.add(new ScoreFactor(slow.size() + " query shape(s) averaging > 100ms", penalty));
        }
        if (!verySlow.isEmpty() || !slow.isEmpty()) {
            recs.add(new Recommendation(verySlow.isEmpty() ? "MEDIUM" : "HIGH", "Optimize the slowest queries",
                    "The slow-query panel lists the top consumers. Analyze the worst offenders with the AI Query Analyzer for index and rewrite suggestions.",
                    null));
        }

        // Temp spill
        if (s.tempBytes() > 100L * 1024 * 1024) {
            score -= 5;
            factors.add(new ScoreFactor("Sorts/hashes spilling to disk (%d MB temp)".formatted(s.tempBytes() / 1024 / 1024), 5));
            recs.add(new Recommendation("LOW", "Reduce temp file usage",
                    "Queries are spilling to disk. Consider raising work_mem for sessions running large sorts or aggregations.",
                    null));
        }

        score = Math.max(score, 5);
        return new HealthAssessment(score, grade(score), factors, recs);
    }

    private String grade(int score) {
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 60) return "C";
        if (score >= 40) return "D";
        return "F";
    }
}
