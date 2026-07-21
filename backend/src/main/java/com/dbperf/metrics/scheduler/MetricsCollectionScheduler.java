package com.dbperf.metrics.scheduler;

import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically snapshots every registered connection so dashboards and AI
 * analysis always have fresh history, even when nobody is clicking
 * "Collect now". Failures mark the connection UNREACHABLE and move on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.collector.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsCollectionScheduler {

    private final DatabaseConnectionRepository connectionRepository;
    private final MetricsCollectorService collectorService;

    @Scheduled(fixedDelayString = "${app.collector.interval-ms:300000}", initialDelayString = "${app.collector.initial-delay-ms:60000}")
    public void collectAll() {
        List<DatabaseConnection> connections = connectionRepository.findAllByMonitoringEnabledTrue();
        if (connections.isEmpty()) {
            return;
        }
        int succeeded = 0;
        for (DatabaseConnection connection : connections) {
            try {
                collectorService.collect(connection);
                succeeded++;
            } catch (Exception e) {
                log.warn("Scheduled collection failed for connection {} ({}): {}",
                        connection.getId(), connection.getName(), e.getMessage());
            }
        }
        log.info("Scheduled collection finished: {}/{} connections snapshotted", succeeded, connections.size());
    }
}
