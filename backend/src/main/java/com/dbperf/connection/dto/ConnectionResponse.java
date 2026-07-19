package com.dbperf.connection.dto;

import com.dbperf.connection.domain.DatabaseConnection;

import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
        UUID id,
        String name,
        String host,
        int port,
        String databaseName,
        String username,
        String sslMode,
        String status,
        Instant lastTestedAt,
        String lastError,
        Instant createdAt) {

    public static ConnectionResponse from(DatabaseConnection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getName(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getUsername(),
                connection.getSslMode().name(),
                connection.getStatus().name(),
                connection.getLastTestedAt(),
                connection.getLastError(),
                connection.getCreatedAt());
    }
}
