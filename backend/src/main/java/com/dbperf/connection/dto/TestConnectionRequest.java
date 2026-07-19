package com.dbperf.connection.dto;

import com.dbperf.connection.domain.SslMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Ad-hoc "test before save" request; nothing is persisted. */
public record TestConnectionRequest(
        @NotBlank(message = "Host is required") String host,
        @Min(1) @Max(65535) int port,
        @NotBlank(message = "Database name is required") String databaseName,
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password,
        SslMode sslMode) {

    public SslMode sslModeOrDefault() {
        return sslMode == null ? SslMode.PREFER : sslMode;
    }
}
