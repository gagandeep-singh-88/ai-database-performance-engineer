package com.dbperf.report.api;

import com.dbperf.report.service.ReportService;
import com.dbperf.report.service.ReportService.GeneratedReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Optimization Reports", description = "Downloadable PDF reports grounded in collected metrics")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{connectionId}/pdf")
    @Operation(summary = "Generate and download the optimization report for a connection")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID connectionId) {
        GeneratedReport report = reportService.generate(connectionId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(report.filename()).build());
        return new ResponseEntity<>(report.pdf(), headers, org.springframework.http.HttpStatus.OK);
    }
}
