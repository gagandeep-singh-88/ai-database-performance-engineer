package com.dbperf.connection.service;

import com.dbperf.connection.domain.SslMode;
import com.dbperf.connection.dto.ConnectionTestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Probes a target database: connectivity, latency, server version, size
 * and pg_stat_statements availability (needed by the Performance Collector).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionProber {

    private final TargetConnectionFactory connectionFactory;

    public ConnectionTestResult probe(String host, int port, String databaseName,
                                      String username, String password, SslMode sslMode) {
        long start = System.currentTimeMillis();
        try (Connection connection = connectionFactory.open(host, port, databaseName, username, password, sslMode);
             Statement statement = connection.createStatement()) {

            String serverVersion = querySingleString(statement, "SELECT current_setting('server_version')");
            String databaseSize = querySingleString(statement,
                    "SELECT pg_size_pretty(pg_database_size(current_database()))");
            boolean pgStatStatements = "1".equals(querySingleString(statement,
                    "SELECT count(*) FROM pg_extension WHERE extname = 'pg_stat_statements'"));
            boolean readOnly = "on".equals(querySingleString(statement,
                    "SELECT current_setting('default_transaction_read_only')"));

            long latency = System.currentTimeMillis() - start;
            String message = pgStatStatements
                    ? "Connected successfully"
                    : "Connected, but pg_stat_statements is not enabled — query statistics will be limited. "
                    + "Enable it with: CREATE EXTENSION pg_stat_statements;";
            return new ConnectionTestResult(true, latency, "PostgreSQL " + serverVersion,
                    databaseSize, pgStatStatements, readOnly, message);
        } catch (SQLException e) {
            long latency = System.currentTimeMillis() - start;
            log.info("Probe failed for {}:{}/{} — {}", host, port, databaseName, e.getMessage());
            return ConnectionTestResult.failure(latency, sanitize(e));
        }
    }

    private String querySingleString(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    /** Keep driver messages useful but drop anything that could echo credentials. */
    private String sanitize(SQLException e) {
        String message = e.getMessage() == null ? "Connection failed" : e.getMessage().split("\n")[0];
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
