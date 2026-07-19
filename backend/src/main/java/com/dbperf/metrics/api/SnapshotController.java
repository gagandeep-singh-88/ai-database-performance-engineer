package com.dbperf.metrics.api;

import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.service.MetricsCollectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connections/{connectionId}/snapshots")
@RequiredArgsConstructor
@Tag(name = "Performance Snapshots", description = "Collect and browse historical performance metrics")
@SecurityRequirement(name = "bearerAuth")
public class SnapshotController {

    private final MetricsCollectorService collectorService;

    @PostMapping
    @Operation(summary = "Collect a snapshot now (pg_stat_statements, activity, locks, db stats)")
    public ResponseEntity<SnapshotDetailResponse> collectNow(@PathVariable UUID connectionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectorService.collectNow(connectionId));
    }

    @GetMapping
    @Operation(summary = "Snapshot history (summaries, newest first)")
    public ResponseEntity<List<SnapshotSummaryResponse>> history(
            @PathVariable UUID connectionId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(collectorService.history(connectionId, limit));
    }

    @GetMapping("/latest")
    @Operation(summary = "Latest snapshot with full drill-down detail")
    public ResponseEntity<SnapshotDetailResponse> latest(@PathVariable UUID connectionId) {
        return ResponseEntity.ok(collectorService.latest(connectionId));
    }
}
