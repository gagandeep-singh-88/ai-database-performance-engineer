package com.dbperf.connection.dto;

import com.dbperf.connection.domain.SslMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConnectionRequest(
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

        @NotBlank(message = "Password is required")
        String password,

        SslMode sslMode) {

    public SslMode sslModeOrDefault() {
        return sslMode == null ? SslMode.PREFER : sslMode;
    }
}
