package com.dbperf.dashboard.api;

import com.dbperf.dashboard.dto.DashboardResponse;
import com.dbperf.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/connections/{connectionId}/dashboard")
@RequiredArgsConstructor
@Tag(name = "Health Dashboard", description = "Health score, trends and recommendations")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Health score, factors, latest snapshot detail, history series and rule-based recommendations")
    public ResponseEntity<DashboardResponse> dashboard(
            @PathVariable UUID connectionId,
            @RequestParam(defaultValue = "100") int historyLimit) {
        return ResponseEntity.ok(dashboardService.dashboard(connectionId, historyLimit));
    }

    @PostMapping("/ai-recommendations")
    @Operation(summary = "Claude-generated recommendations grounded in the latest snapshot")
    public ResponseEntity<List<DashboardResponse.Recommendation>> aiRecommendations(
            @PathVariable UUID connectionId) {
        return ResponseEntity.ok(dashboardService.aiRecommendations(connectionId));
    }
}
