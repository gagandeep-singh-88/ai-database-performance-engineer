package com.dbperf.report.service;

import com.dbperf.ai.AiReportSummary;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.dashboard.service.HealthScoreCalculator;
import com.dbperf.metrics.domain.MetricSnapshot;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private QueryAnalysisAi ai;
    @Mock
    private ConnectionAccess connectionAccess;
    @Mock
    private MetricSnapshotRepository snapshotRepository;
    @Mock
    private MetricsCollectorService collectorService;
    @Mock
    private QueryAnalysisRepository analysisRepository;
    @Mock
    private CurrentUserService currentUserService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(ai, connectionAccess, snapshotRepository, collectorService,
                new HealthScoreCalculator(), analysisRepository, currentUserService,
                new ReportHtmlRenderer(), new PdfRenderer());
        lenient().when(currentUserService.require()).thenReturn(
                User.builder().id(UUID.randomUUID()).email("j@e.com").passwordHash("h")
                        .fullName("J").role(Role.USER).build());
        lenient().when(ai.model()).thenReturn("claude-opus-4-8");
    }

    private DatabaseConnection connection(String name) {
        DatabaseConnection connection = mock(DatabaseConnection.class);
        lenient().when(connection.getName()).thenReturn(name);
        lenient().when(connection.getDatabaseName()).thenReturn("shop");
        lenient().when(connection.getHost()).thenReturn("db.example.com");
        return connection;
    }

    private SnapshotDetailResponse detail() {
        return new SnapshotDetailResponse(
                new SnapshotSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                        42_000_000L, 3, 0, 0, 0, 0.99, 1000, 5, 0, 0),
                List.of(new QueryStat("1", "SELECT * FROM orders WHERE status = $1", 900, 45_000, 50.0, 100, 90, 10, 0.9)),
                List.<SessionInfo>of(), List.<LockInfo>of(),
                List.of(new TableStat("orders", 12_000, 9_000_000, 40, 250_000)));
    }

    private AiReportSummary summary() {
        return new AiReportSummary("All good overall.", List.of("orders is seq-scanned heavily"),
                List.of(new AiReportSummary.ActionItem("HIGH", "Add index on orders.customer_id",
                        "detail", "CREATE INDEX CONCURRENTLY idx ON orders (customer_id);", "~20x")));
    }

    @Test
    void generatesPdfWithSanitizedFilenameAndGroundedPrompt() {
        UUID connectionId = UUID.randomUUID();
        DatabaseConnection prodConnection = connection("Prod DB (EU) #1");
        when(connectionAccess.requireOwned(connectionId)).thenReturn(prodConnection);
        MetricSnapshot snapshot = mock(MetricSnapshot.class);
        when(snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findAllByConnectionIdOrderByCapturedAtDesc(eq(connectionId), any()))
                .thenReturn(List.of());
        when(collectorService.toDetail(snapshot)).thenReturn(detail());
        when(analysisRepository.findAllByUserIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(ai.structured(anyString(), anyString(), eq(AiReportSummary.class))).thenReturn(summary());

        ReportService.GeneratedReport report = service.generate(connectionId);

        assertThat(new String(report.pdf(), 0, 5)).isEqualTo("%PDF-");
        assertThat(report.filename()).matches("dbperf-report-prod-db-eu-1-\\d{4}-\\d{2}-\\d{2}\\.pdf");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(anyString(), promptCaptor.capture(), eq(AiReportSummary.class));
        assertThat(promptCaptor.getValue())
                .contains("Health score:")
                .contains("SELECT * FROM orders")
                .contains("orders: ~250,000 rows");
    }

    @Test
    void failsClearlyWhenNoSnapshotExists() {
        UUID connectionId = UUID.randomUUID();
        DatabaseConnection prodConnection = connection("prod");
        when(connectionAccess.requireOwned(connectionId)).thenReturn(prodConnection);
        when(snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(connectionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No snapshots");
    }
}
