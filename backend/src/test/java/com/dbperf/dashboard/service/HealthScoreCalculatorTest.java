package com.dbperf.dashboard.service;

import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HealthScoreCalculatorTest {

    private final HealthScoreCalculator calculator = new HealthScoreCalculator();

    private SnapshotDetailResponse snapshot(SnapshotSummaryResponse summary,
                                            List<QueryStat> queries, List<TableStat> tables) {
        return new SnapshotDetailResponse(summary, queries, List.<SessionInfo>of(), List.<LockInfo>of(), tables);
    }

    private SnapshotSummaryResponse summary(int blocked, int idleInTx, int waitingLocks,
                                            Double cacheHit, long deadlocks, long tempBytes) {
        return new SnapshotSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                42_000_000L, 3, idleInTx, blocked, waitingLocks, cacheHit, 1000, 5, deadlocks, tempBytes);
    }

    @Test
    void healthyDatabaseScoresAGrade() {
        HealthAssessment assessment = calculator.assess(snapshot(
                summary(0, 0, 0, 0.995, 0, 0),
                List.of(new QueryStat("1", "SELECT 1", 100, 500, 5.0, 100, 99, 1, 0.99)),
                List.of(new TableStat("small_table", 10, 5_000, 100, 1_000))));

        assertThat(assessment.score()).isGreaterThanOrEqualTo(90);
        assertThat(assessment.grade()).isEqualTo("A");
        assertThat(assessment.factors()).isEmpty();
    }

    @Test
    void degradedDatabaseAccumulatesPenaltiesWithExplanations() {
        HealthAssessment assessment = calculator.assess(snapshot(
                summary(2, 1, 3, 0.85, 4, 200L * 1024 * 1024),
                List.of(
                        new QueryStat("1", "SELECT ... slow", 50, 90_000, 1800.0, 100, 50, 50, 0.5),
                        new QueryStat("2", "SELECT ... medium", 200, 30_000, 150.0, 100, 80, 20, 0.8)),
                List.of(new TableStat("orders", 4000, 120_000_000, 3, 100_000))));

        assertThat(assessment.score()).isLessThan(40);
        assertThat(assessment.grade()).isIn("D", "F");
        assertThat(assessment.factors())
                .extracting(f -> f.label())
                .anySatisfy(label -> assertThat(label).contains("blocked"))
                .anySatisfy(label -> assertThat(label).contains("Cache hit"))
                .anySatisfy(label -> assertThat(label).contains("hotspot"));
        assertThat(assessment.recommendations())
                .extracting(r -> r.title())
                .anySatisfy(title -> assertThat(title).contains("orders"));
    }

    @Test
    void scoreNeverGoesBelowFloor() {
        HealthAssessment assessment = calculator.assess(snapshot(
                summary(10, 10, 10, 0.5, 100, 10L * 1024 * 1024 * 1024),
                List.of(
                        new QueryStat("1", "q1", 1, 5000, 5000.0, 1, 0, 100, 0.0),
                        new QueryStat("2", "q2", 1, 4000, 4000.0, 1, 0, 100, 0.0),
                        new QueryStat("3", "q3", 1, 900, 300.0, 1, 0, 100, 0.0)),
                List.of(
                        new TableStat("t1", 100, 50_000_000, 0, 1_000_000),
                        new TableStat("t2", 100, 60_000_000, 0, 1_000_000),
                        new TableStat("t3", 100, 70_000_000, 0, 1_000_000))));

        assertThat(assessment.score()).isGreaterThanOrEqualTo(5);
        assertThat(assessment.grade()).isEqualTo("F");
    }

    @Test
    void seqScansOnSmallTablesAreNotFlagged() {
        HealthAssessment assessment = calculator.assess(snapshot(
                summary(0, 0, 0, 0.99, 0, 0),
                List.of(),
                List.of(new TableStat("lookup_table", 100_000, 500_000, 2, 500))));

        assertThat(assessment.factors()).noneSatisfy(f -> assertThat(f.label()).contains("hotspot"));
    }
}
