package com.dbperf.connection.api;

import com.dbperf.connection.dto.ConnectionResponse;
import com.dbperf.connection.dto.ConnectionTestResult;
import com.dbperf.connection.dto.CreateConnectionRequest;
import com.dbperf.connection.dto.TestConnectionRequest;
import com.dbperf.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
@Tag(name = "Database Connections", description = "Manage monitored PostgreSQL targets (read-only access)")
@SecurityRequirement(name = "bearerAuth")
public class ConnectionController {

    private final ConnectionService connectionService;

    @PostMapping
    @Operation(summary = "Add a PostgreSQL target; the password is stored in the secret store, never in the app DB")
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody CreateConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(request));
    }

    @GetMapping
    @Operation(summary = "List the current user's connections")
    public ResponseEntity<List<ConnectionResponse>> list() {
        return ResponseEntity.ok(connectionService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one connection")
    public ResponseEntity<ConnectionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(connectionService.get(id));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test a stored connection and refresh its health status")
    public ResponseEntity<ConnectionTestResult> test(@PathVariable UUID id) {
        return ResponseEntity.ok(connectionService.test(id));
    }

    @PostMapping("/test")
    @Operation(summary = "Ad-hoc connectivity test before saving (nothing persisted)")
    public ResponseEntity<ConnectionTestResult> testAdhoc(@Valid @RequestBody TestConnectionRequest request) {
        return ResponseEntity.ok(connectionService.testAdhoc(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a connection and its stored secret")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
