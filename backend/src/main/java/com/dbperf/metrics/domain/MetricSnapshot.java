package com.dbperf.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metric_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "db_size_bytes", nullable = false)
    private long dbSizeBytes;

    @Column(name = "active_sessions", nullable = false)
    private int activeSessions;

    @Column(name = "idle_in_transaction", nullable = false)
    private int idleInTransaction;

    @Column(name = "blocked_sessions", nullable = false)
    private int blockedSessions;

    @Column(name = "waiting_locks", nullable = false)
    private int waitingLocks;

    @Column(name = "cache_hit_ratio")
    private Double cacheHitRatio;

    @Column(name = "xact_commit", nullable = false)
    private long xactCommit;

    @Column(name = "xact_rollback", nullable = false)
    private long xactRollback;

    @Column(nullable = false)
    private long deadlocks;

    @Column(name = "temp_bytes", nullable = false)
    private long tempBytes;

    /** JSON array of QueryStat */
    @Column(name = "top_queries", columnDefinition = "TEXT")
    private String topQueries;

    /** JSON array of SessionInfo */
    @Column(name = "sessions_detail", columnDefinition = "TEXT")
    private String sessionsDetail;

    /** JSON array of LockInfo */
    @Column(name = "locks_detail", columnDefinition = "TEXT")
    private String locksDetail;

    /** JSON array of TableStat */
    @Column(name = "table_stats", columnDefinition = "TEXT")
    private String tableStats;
}
