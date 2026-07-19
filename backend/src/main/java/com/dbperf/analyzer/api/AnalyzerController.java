package com.dbperf.analyzer.api;

import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.analyzer.dto.AnalyzeRequest;
import com.dbperf.analyzer.dto.QueryAnalysisResponse;
import com.dbperf.analyzer.service.QueryAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyzer")
@RequiredArgsConstructor
@Tag(name = "AI Query Analyzer", description = "Claude-powered SQL and execution plan analysis")
@SecurityRequirement(name = "bearerAuth")
public class AnalyzerController {

    private final QueryAnalysisService analysisService;

    @PostMapping("/analyze")
    @Operation(summary = "Analyze SQL and/or EXPLAIN output; with a connection, the plan and schema stats are fetched live")
    public ResponseEntity<QueryAnalysisResponse> analyze(@RequestBody AnalyzeRequest request) {
        return ResponseEntity.ok(analysisService.analyze(request));
    }

    @GetMapping("/history")
    @Operation(summary = "Past analyses, newest first")
    public ResponseEntity<List<AnalysisHistoryItem>> history(@RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(analysisService.history(limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full stored analysis")
    public ResponseEntity<QueryAnalysisResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(analysisService.get(id));
    }
}
