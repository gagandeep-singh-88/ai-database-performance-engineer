package com.dbperf.metrics.service;

import com.dbperf.metrics.dto.CollectedMetrics;
import com.dbperf.metrics.dto.LockInfo;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SessionInfo;
import com.dbperf.metrics.dto.TableStat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes the read-only statistics queries against a target database.
 * Every section degrades gracefully (e.g. pg_stat_statements missing)
 * so a partially-privileged monitoring user still yields a snapshot.
 */
@Slf4j
@Component
public class TargetMetricsReader {

    private static final String DB_STATS_SQL = """
            SELECT pg_database_size(current_database())          AS db_size,
                   s.xact_commit, s.xact_rollback,
                   s.blks_read, s.blks_hit,
                   s.temp_bytes, s.deadlocks
            FROM pg_stat_database s
            WHERE s.datname = current_database()
            """;

    private static final String SESSION_COUNTS_SQL = """
            SELECT count(*) FILTER (WHERE state = 'active')               AS active,
                   count(*) FILTER (WHERE state = 'idle in transaction')  AS idle_in_tx,
                   count(*) FILTER (WHERE cardinality(pg_blocking_pids(pid)) > 0) AS blocked
            FROM pg_stat_activity
            WHERE datname = current_database() AND backend_type = 'client backend'
            """;

    private static final String WAITING_LOCKS_SQL =
            "SELECT count(*) FROM pg_locks WHERE NOT granted";

    private static final String TOP_QUERIES_SQL = """
            SELECT queryid::text AS query_id, left(query, 1000) AS query, calls,
                   total_exec_time, mean_exec_time, rows,
                   shared_blks_hit, shared_blks_read
            FROM pg_stat_statements
            WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
              AND query NOT ILIKE 'EXPLAIN%'
              AND query NOT ILIKE '%pg_stat_statements%'
              AND query NOT ILIKE '%pg_stat_activity%'
              AND query NOT ILIKE '%pg_database_size%'
            ORDER BY total_exec_time DESC
            LIMIT 20
            """;

    private static final String SESSIONS_SQL = """
            SELECT pid, usename, state, wait_event_type, wait_event,
                   EXTRACT(EPOCH FROM (now() - query_start))::bigint AS duration_seconds,
                   left(query, 500) AS query
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND backend_type = 'client backend'
              AND pid <> pg_backend_pid()
            ORDER BY query_start NULLS LAST
            LIMIT 50
            """;

    private static final String BLOCKING_SQL = """
            SELECT blocked.pid                 AS blocked_pid,
                   left(blocked.query, 300)    AS blocked_query,
                   blocking.pid                AS blocking_pid,
                   left(blocking.query, 300)   AS blocking_query
            FROM pg_stat_activity blocked
            JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS b(blocking_pid) ON true
            JOIN pg_stat_activity blocking ON blocking.pid = b.blocking_pid
            WHERE blocked.datname = current_database()
            LIMIT 25
            """;

    // n_live_tup can read 0 right after a server restart (runtime stats reset),
    // so fall back to pg_class.reltuples, which ANALYZE persists in the catalog.
    private static final String TABLE_STATS_SQL = """
            SELECT s.relname, s.seq_scan, s.seq_tup_read, COALESCE(s.idx_scan, 0) AS idx_scan,
                   GREATEST(s.n_live_tup, c.reltuples::bigint, 0) AS live_rows
            FROM pg_stat_user_tables s
            JOIN pg_class c ON c.oid = s.relid
            ORDER BY s.seq_tup_read DESC
            LIMIT 15
            """;

    public CollectedMetrics read(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            long dbSize = 0;
            long xactCommit = 0;
            long xactRollback = 0;
            long blksRead = 0;
            long blksHit = 0;
            long tempBytes = 0;
            long deadlocks = 0;
            try (ResultSet rs = statement.executeQuery(DB_STATS_SQL)) {
                if (rs.next()) {
                    dbSize = rs.getLong("db_size");
                    xactCommit = rs.getLong("xact_commit");
                    xactRollback = rs.getLong("xact_rollback");
                    blksRead = rs.getLong("blks_read");
                    blksHit = rs.getLong("blks_hit");
                    tempBytes = rs.getLong("temp_bytes");
                    deadlocks = rs.getLong("deadlocks");
                }
            }
            Double cacheHitRatio = (blksHit + blksRead) > 0
                    ? (double) blksHit / (blksHit + blksRead)
                    : null;

            int active = 0;
            int idleInTx = 0;
            int blocked = 0;
            try (ResultSet rs = statement.executeQuery(SESSION_COUNTS_SQL)) {
                if (rs.next()) {
                    active = rs.getInt("active");
                    idleInTx = rs.getInt("idle_in_tx");
                    blocked = rs.getInt("blocked");
                }
            }

            int waitingLocks = 0;
            try (ResultSet rs = statement.executeQuery(WAITING_LOCKS_SQL)) {
                if (rs.next()) {
                    waitingLocks = rs.getInt(1);
                }
            }

            return new CollectedMetrics(dbSize, active, idleInTx, blocked, waitingLocks,
                    cacheHitRatio, xactCommit, xactRollback, deadlocks, tempBytes,
                    readTopQueries(connection), readSessions(connection),
                    readBlockingLocks(connection), readTableStats(connection));
        }
    }

    private List<QueryStat> readTopQueries(Connection connection) {
        List<QueryStat> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(TOP_QUERIES_SQL)) {
            while (rs.next()) {
                long hit = rs.getLong("shared_blks_hit");
                long read = rs.getLong("shared_blks_read");
                result.add(new QueryStat(
                        rs.getString("query_id"),
                        rs.getString("query"),
                        rs.getLong("calls"),
                        rs.getDouble("total_exec_time"),
                        rs.getDouble("mean_exec_time"),
                        rs.getLong("rows"),
                        hit, read,
                        (hit + read) > 0 ? (double) hit / (hit + read) : 1.0));
            }
        } catch (SQLException e) {
            // extension missing or no privilege — snapshot continues without query stats
            log.debug("pg_stat_statements unavailable: {}", e.getMessage());
        }
        return result;
    }

    private List<SessionInfo> readSessions(Connection connection) {
        List<SessionInfo> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SESSIONS_SQL)) {
            while (rs.next()) {
                long duration = rs.getLong("duration_seconds");
                result.add(new SessionInfo(
                        rs.getInt("pid"),
                        rs.getString("usename"),
                        rs.getString("state"),
                        rs.getString("wait_event_type"),
                        rs.getString("wait_event"),
                        rs.wasNull() ? null : duration,
                        rs.getString("query")));
            }
        } catch (SQLException e) {
            log.debug("pg_stat_activity read failed: {}", e.getMessage());
        }
        return result;
    }

    private List<LockInfo> readBlockingLocks(Connection connection) {
        List<LockInfo> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(BLOCKING_SQL)) {
            while (rs.next()) {
                result.add(new LockInfo(
                        rs.getInt("blocked_pid"),
                        rs.getString("blocked_query"),
                        rs.getInt("blocking_pid"),
                        rs.getString("blocking_query")));
            }
        } catch (SQLException e) {
            log.debug("blocking-locks read failed: {}", e.getMessage());
        }
        return result;
    }

    private List<TableStat> readTableStats(Connection connection) {
        List<TableStat> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(TABLE_STATS_SQL)) {
            while (rs.next()) {
                result.add(new TableStat(
                        rs.getString("relname"),
                        rs.getLong("seq_scan"),
                        rs.getLong("seq_tup_read"),
                        rs.getLong("idx_scan"),
                        rs.getLong("live_rows")));
            }
        } catch (SQLException e) {
            log.debug("table-stats read failed: {}", e.getMessage());
        }
        return result;
    }
}
