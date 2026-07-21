package com.dbperf.connection.dto;

import com.dbperf.connection.domain.SslMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edit an existing connection's details. {@code password} is optional — blank
 * or omitted keeps the currently stored secret; a non-blank value replaces it.
 */
public record UpdateConnectionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "Host is required")
        @Size(max = 255)
        String host,

        @Min(value = 1, message = "Port must be between 1 and 65535")
        @Max(value = 65535, message = "Port must be between 1 and 65535")
        int port,

        @NotBlank(message = "Database name is required")
        @Size(max = 120)
        String databaseName,

        @NotBlank(message = "Username is required")
        @Size(max = 120)
        String username,

        String password,

        SslMode sslMode) {

    public SslMode sslModeOrDefault() {
        return sslMode == null ? SslMode.PREFER : sslMode;
    }

    public boolean hasNewPassword() {
        return password != null && !password.isBlank();
    }
}
