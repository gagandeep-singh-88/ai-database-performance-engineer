package com.dbperf.report.service;

import com.dbperf.ai.AiReportSummary;
import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.dashboard.dto.DashboardResponse.Recommendation;
import com.dbperf.dashboard.dto.DashboardResponse.ScoreFactor;
import com.dbperf.dashboard.service.HealthScoreCalculator.HealthAssessment;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.report.dto.ReportModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRenderingTest {

    private final ReportHtmlRenderer htmlRenderer = new ReportHtmlRenderer();
    private final PdfRenderer pdfRenderer = new PdfRenderer();

    private ReportModel sampleModel() {
        SnapshotSummaryResponse summary = new SnapshotSummaryResponse(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-19T10:00:00Z"), 42_000_000L, 3, 1, 2, 1, 0.87, 1000, 5, 1, 1024);
        SnapshotDetailResponse latest = new SnapshotDetailResponse(summary,
                List.of(new QueryStat("1", "SELECT * FROM orders o JOIN customers c ON c.id = o.customer_id WHERE o.status = 'pending' AND note < '<script>'",
                        900, 45_000, 50.0, 100, 90, 10, 0.9)),
                List.<SessionInfo>of(), List.<LockInfo>of(),
                List.of(new TableStat("orders", 12_000, 9_000_000, 40, 250_000)));
        HealthAssessment health = new HealthAssessment(58, "C",
                List.of(new ScoreFactor("Cache hit ratio 87.0% (< 90%)", 15)),
                List.<Recommendation>of());
        AiReportSummary aiSummary = new AiReportSummary(
                "The database is under moderate pressure.\n\nMost load comes from the pending-orders query.",
                List.of("orders is seq-scanned 12,000 times", "Cache hit ratio is below target"),
                List.of(new AiReportSummary.ActionItem("HIGH", "Add index on orders.customer_id",
                        "Turns the dominant seq scan into an index scan; safe with CONCURRENTLY & no long locks",
                        "CREATE INDEX CONCURRENTLY idx_orders_customer_id ON orders (customer_id);",
                        "~20x on the pending-orders query")));
        return new ReportModel("prod-db", "shop", "db.example.com", Instant.parse("2026-07-19T12:00:00Z"),
                "claude-opus-4-8", health, latest,
                List.of(summary, new SnapshotSummaryResponse(UUID.randomUUID(), UUID.randomUUID(),
                        Instant.parse("2026-07-19T09:55:00Z"), 42_000_000L, 2, 0, 0, 0, 0.91, 900, 4, 0, 0)),
                List.of(new AnalysisHistoryItem(UUID.randomUUID(), null, "SELECT …", "Seq scan on orders",
                        "claude-opus-4-8", Instant.parse("2026-07-18T12:00:00Z"))),
                aiSummary);
    }

    @Test
    void htmlContainsAllSectionsWithEscapedContent() {
        String html = htmlRenderer.render(sampleModel());

        assertThat(html)
                .contains("Database Optimization Report")
                .contains("Executive Summary")
                .contains("Prioritized Action Plan")
                .contains("Top Queries by Total Time")
                .contains("Recent Snapshot Trend")
                .contains("CREATE INDEX CONCURRENTLY")
                // SQL containing <script> and quotes must be escaped, never raw
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void pipelineProducesValidPdfBytes() {
        byte[] pdf = pdfRenderer.toPdf(htmlRenderer.render(sampleModel()));

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
