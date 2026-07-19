package com.dbperf.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @Min(1) long expirationMinutes) {
}
