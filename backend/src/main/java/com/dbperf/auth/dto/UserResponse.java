package com.dbperf.auth.dto;

import com.dbperf.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
