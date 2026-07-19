package com.dbperf.metrics.service;

import com.dbperf.common.exception.TargetUnreachableException;
import com.dbperf.connection.domain.ConnectionStatus;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.connection.service.TargetConnectionFactory;
import com.dbperf.metrics.domain.MetricSnapshot;
import com.dbperf.metrics.dto.CollectedMetrics;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.secrets.SecretStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollectorService {

    private static final TypeReference<List<QueryStat>> QUERY_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<SessionInfo>> SESSION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<LockInfo>> LOCK_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<TableStat>> TABLE_LIST = new TypeReference<>() {
    };

    private final MetricSnapshotRepository snapshotRepository;
    private final DatabaseConnectionRepository connectionRepository;
    private final ConnectionAccess connectionAccess;
    private final TargetConnectionFactory connectionFactory;
    private final TargetMetricsReader metricsReader;
    private final SecretStore secretStore;
    private final ObjectMapper objectMapper;

    /** API entry point: collect immediately for a connection the caller owns. */
    public SnapshotDetailResponse collectNow(UUID connectionId) {
        DatabaseConnection connection = connectionAccess.requireOwned(connectionId);
        return toDetail(collect(connection));
    }

    /**
     * Core collection pass. Also used by the background scheduler, so it
     * must not depend on a request/security context.
     */
    public MetricSnapshot collect(DatabaseConnection connection) {
        String password = secretStore.retrieve(connection.getSecretRef());
        try (Connection target = connectionFactory.open(connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getUsername(), password, connection.getSslMode())) {

            CollectedMetrics metrics = metricsReader.read(target);
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .connectionId(connection.getId())
                    .dbSizeBytes(metrics.dbSizeBytes())
                    .activeSessions(metrics.activeSessions())
                    .idleInTransaction(metrics.idleInTransaction())
                    .blockedSessions(metrics.blockedSessions())
                    .waitingLocks(metrics.waitingLocks())
                    .cacheHitRatio(metrics.cacheHitRatio())
                    .xactCommit(metrics.xactCommit())
                    .xactRollback(metrics.xactRollback())
                    .deadlocks(metrics.deadlocks())
                    .tempBytes(metrics.tempBytes())
                    .topQueries(toJson(metrics.topQueries()))
                    .sessionsDetail(toJson(metrics.sessions()))
                    .locksDetail(toJson(metrics.locks()))
                    .tableStats(toJson(metrics.tableStats()))
                    .build();
            snapshot = snapshotRepository.saveAndFlush(snapshot);

            connection.setStatus(ConnectionStatus.HEALTHY);
            connection.setLastTestedAt(Instant.now());
            connection.setLastError(null);
            connectionRepository.save(connection);

            log.info("Snapshot {} captured for connection {} ({} queries, {} sessions, {} blocked)",
                    snapshot.getId(), connection.getId(), metrics.topQueries().size(),
                    metrics.sessions().size(), metrics.blockedSessions());
            return snapshot;
        } catch (SQLException e) {
            connection.setStatus(ConnectionStatus.UNREACHABLE);
            connection.setLastTestedAt(Instant.now());
            connection.setLastError(firstLine(e.getMessage()));
            connectionRepository.save(connection);
            throw new TargetUnreachableException("Metrics collection failed: " + firstLine(e.getMessage()));
        }
    }

    public List<SnapshotSummaryResponse> history(UUID connectionId, int limit) {
        connectionAccess.requireOwned(connectionId);
        return snapshotRepository
                .findAllByConnectionIdOrderByCapturedAtDesc(connectionId, PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(SnapshotSummaryResponse::from)
                .toList();
    }

    public SnapshotDetailResponse latest(UUID connectionId) {
        connectionAccess.requireOwned(connectionId);
        return snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId)
                .map(this::toDetail)
                .orElseThrow(() -> new com.dbperf.common.exception.ResourceNotFoundException(
                        "No snapshots collected yet for this connection"));
    }

    public SnapshotDetailResponse toDetail(MetricSnapshot snapshot) {
        return new SnapshotDetailResponse(
                SnapshotSummaryResponse.from(snapshot),
                fromJson(snapshot.getTopQueries(), QUERY_LIST),
                fromJson(snapshot.getSessionsDetail(), SESSION_LIST),
                fromJson(snapshot.getLocksDetail(), LOCK_LIST),
                fromJson(snapshot.getTableStats(), TABLE_LIST));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize snapshot detail", e);
        }
    }

    private <T> List<T> fromJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Corrupt snapshot detail JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String firstLine(String message) {
        if (message == null) {
            return "Connection failed";
        }
        String line = message.split("\n")[0];
        return line.length() > 300 ? line.substring(0, 300) : line;
    }
}
