package com.dbperf.connection.service;

import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared ownership guard: resolves a connection only if it belongs to the
 * authenticated user. Cross-user access is indistinguishable from a
 * missing resource (404) by design.
 */
@Service
@RequiredArgsConstructor
public class ConnectionAccess {

    private final DatabaseConnectionRepository connectionRepository;
    private final CurrentUserService currentUserService;

    public DatabaseConnection requireOwned(UUID connectionId) {
        UUID userId = currentUserService.require().getId();
        return connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
    }
}
