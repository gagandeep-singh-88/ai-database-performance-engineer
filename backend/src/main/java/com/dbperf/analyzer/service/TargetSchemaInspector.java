package com.dbperf.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounds the AI analysis in reality: runs EXPLAIN against the target
 * (read-only session) and collects index definitions + table statistics
 * for the tables referenced by the query.
 */
@Slf4j
@Component
public class TargetSchemaInspector {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:from|join|update|into)\\s+([a-zA-Z_][a-zA-Z0-9_.]*)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KEYWORD_FALSE_POSITIVES = Set.of("select", "lateral", "unnest", "generate_series");

    /**
     * Runs EXPLAIN (optionally ANALYZE) on the target. Plain EXPLAIN never
     * executes the query; EXPLAIN ANALYZE does execute it, but the session
     * is read-only so writes are rejected by PostgreSQL.
     */
    public String explain(Connection connection, String sql, boolean analyze) {
        String cleaned = sql.strip();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.contains(";")) {
            return null; // multiple statements — don't try to explain
        }
        String options = analyze ? "ANALYZE, BUFFERS, VERBOSE, COSTS" : "VERBOSE, COSTS";
        StringBuilder plan = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("EXPLAIN (" + options + ") " + cleaned)) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
            return plan.toString();
        } catch (SQLException e) {
            log.info("EXPLAIN failed: {}", e.getMessage());
            return "EXPLAIN failed on target: " + firstLine(e.getMessage());
        }
    }

    /** Index definitions + table statistics for the tables the query touches. */
    public String schemaContext(Connection connection, String sql) {
        Set<String> tables = extractTableNames(sql);
        if (tables.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder();
        try {
            Array tableArray = connection.createArrayOf("text", tables.toArray());

            context.append("### Table statistics\n");
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT s.relname,
                           GREATEST(s.n_live_tup, c.reltuples::bigint, 0) AS live_rows,
                           pg_size_pretty(pg_total_relation_size(c.oid))  AS total_size,
                           s.seq_scan, COALESCE(s.idx_scan, 0) AS idx_scan,
                           s.last_analyze, s.last_autoanalyze
                    FROM pg_stat_user_tables s
                    JOIN pg_class c ON c.oid = s.relid
                    WHERE s.relname = ANY(?)
                    """)) {
                ps.setArray(1, tableArray);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        context.append("- %s: ~%,d rows, %s on disk, %d seq scans vs %d index scans\n".formatted(
                                rs.getString("relname"), rs.getLong("live_rows"), rs.getString("total_size"),
                                rs.getLong("seq_scan"), rs.getLong("idx_scan")));
                    }
                }
            }

            context.append("\n### Existing indexes\n");
            boolean anyIndex = false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ANY(?) ORDER BY tablename")) {
                ps.setArray(1, tableArray);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        context.append("- ").append(rs.getString(1)).append('\n');
                        anyIndex = true;
                    }
                }
            }
            if (!anyIndex) {
                context.append("- (none beyond what is listed — tables may rely entirely on primary keys or have no indexes)\n");
            }
            return context.toString();
        } catch (SQLException e) {
            log.info("Schema context collection failed: {}", e.getMessage());
            return null;
        }
    }

    static Set<String> extractTableNames(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        if (sql == null) {
            return tables;
        }
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            // strip schema qualifier; keep bare table name for pg_stat_user_tables
            if (name.contains(".")) {
                name = name.substring(name.lastIndexOf('.') + 1);
            }
            if (!KEYWORD_FALSE_POSITIVES.contains(name)) {
                tables.add(name);
            }
        }
        return tables;
    }

    private String firstLine(String message) {
        return message == null ? "unknown error" : message.split("\n")[0];
    }
}
