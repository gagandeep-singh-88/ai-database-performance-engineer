package com.dbperf.metrics.service;

import com.dbperf.common.exception.TargetUnreachableException;
import com.dbperf.connection.domain.ConnectionStatus;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.domain.SslMode;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.connection.service.TargetConnectionFactory;
import com.dbperf.metrics.domain.MetricSnapshot;
import com.dbperf.metrics.dto.CollectedMetrics;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.secrets.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsCollectorServiceTest {

    @Mock
    private MetricSnapshotRepository snapshotRepository;
    @Mock
    private DatabaseConnectionRepository connectionRepository;
    @Mock
    private ConnectionAccess connectionAccess;
    @Mock
    private TargetConnectionFactory connectionFactory;
    @Mock
    private TargetMetricsReader metricsReader;
    @Mock
    private SecretStore secretStore;
    @Mock
    private Connection targetConnection;

    private MetricsCollectorService service;
    private DatabaseConnection connection;

    @BeforeEach
    void setUp() {
        service = new MetricsCollectorService(snapshotRepository, connectionRepository, connectionAccess,
                connectionFactory, metricsReader, secretStore, new ObjectMapper());
        connection = DatabaseConnection.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .name("shop").host("db").port(5432).databaseName("shop")
                .username("monitor").secretRef("local:abc").sslMode(SslMode.PREFER)
                .status(ConnectionStatus.UNKNOWN)
                .build();
    }

    private CollectedMetrics sampleMetrics() {
        return new CollectedMetrics(
                42_000_000L, 3, 1, 1, 2, 0.985, 1000, 5, 0, 0,
                List.of(new QueryStat("123", "SELECT * FROM orders WHERE customer_id = $1",
                        500, 12000.5, 24.0, 5000, 90, 10, 0.9)),
                List.of(new SessionInfo(101, "app", "active", "Lock", "relation", 12L, "UPDATE orders ...")),
                List.of(new LockInfo(101, "UPDATE orders ...", 99, "VACUUM")),
                List.of(new TableStat("orders", 4000, 120_000_000, 3, 100_000)));
    }

    @Test
    void collectPersistsSnapshotWithJsonDetailsAndMarksHealthy() throws SQLException {
        when(secretStore.retrieve("local:abc")).thenReturn("pw");
        when(connectionFactory.open(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(targetConnection);
        when(metricsReader.read(targetConnection)).thenReturn(sampleMetrics());
        when(snapshotRepository.saveAndFlush(any(MetricSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MetricSnapshot snapshot = service.collect(connection);

        assertThat(snapshot.getActiveSessions()).isEqualTo(3);
        assertThat(snapshot.getCacheHitRatio()).isEqualTo(0.985);
        assertThat(snapshot.getTopQueries()).contains("customer_id");
        assertThat(snapshot.getTableStats()).contains("orders");
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.HEALTHY);
        verify(connectionRepository).save(connection);
    }

    @Test
    void collectMarksUnreachableOnSqlException() throws SQLException {
        when(secretStore.retrieve("local:abc")).thenReturn("pw");
        when(connectionFactory.open(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new SQLException("Connection refused"));

        assertThatThrownBy(() -> service.collect(connection))
                .isInstanceOf(TargetUnreachableException.class)
                .hasMessageContaining("Connection refused");

        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.UNREACHABLE);
        verify(connectionRepository).save(connection);
    }

    @Test
    void collectNowChecksOwnershipAndReturnsParsedDetail() throws SQLException {
        when(connectionAccess.requireOwned(connection.getId())).thenReturn(connection);
        when(secretStore.retrieve("local:abc")).thenReturn("pw");
        when(connectionFactory.open(anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                .thenReturn(targetConnection);
        when(metricsReader.read(targetConnection)).thenReturn(sampleMetrics());
        when(snapshotRepository.saveAndFlush(any(MetricSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SnapshotDetailResponse detail = service.collectNow(connection.getId());

        // JSON round trip: what went in as records must come back out intact
        assertThat(detail.topQueries()).hasSize(1);
        assertThat(detail.topQueries().get(0).calls()).isEqualTo(500);
        assertThat(detail.sessions().get(0).waitEvent()).isEqualTo("relation");
        assertThat(detail.locks().get(0).blockingPid()).isEqualTo(99);
        assertThat(detail.tableStats().get(0).seqTupRead()).isEqualTo(120_000_000L);
        ArgumentCaptor<MetricSnapshot> captor = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getConnectionId()).isEqualTo(connection.getId());
    }
}
