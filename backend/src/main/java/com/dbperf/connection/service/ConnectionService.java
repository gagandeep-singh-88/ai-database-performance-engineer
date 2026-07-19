package com.dbperf.connection.service;

import com.dbperf.common.exception.DuplicateResourceException;
import com.dbperf.connection.domain.ConnectionStatus;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.dto.ConnectionResponse;
import com.dbperf.connection.dto.ConnectionTestResult;
import com.dbperf.connection.dto.CreateConnectionRequest;
import com.dbperf.connection.dto.TestConnectionRequest;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.secrets.SecretStore;
import com.dbperf.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final DatabaseConnectionRepository connectionRepository;
    private final SecretStore secretStore;
    private final ConnectionProber prober;
    private final CurrentUserService currentUserService;
    private final ConnectionAccess connectionAccess;

    @Transactional
    public ConnectionResponse create(CreateConnectionRequest request) {
        UUID userId = currentUserService.require().getId();
        if (connectionRepository.existsByUserIdAndNameIgnoreCase(userId, request.name().trim())) {
            throw new DuplicateResourceException("A connection named '%s' already exists".formatted(request.name().trim()));
        }

        String secretRef = secretStore.store("dbconn-" + UUID.randomUUID(), request.password());
        DatabaseConnection connection = DatabaseConnection.builder()
                .userId(userId)
                .name(request.name().trim())
                .host(request.host().trim())
                .port(request.port())
                .databaseName(request.databaseName().trim())
                .username(request.username().trim())
                .secretRef(secretRef)
                .sslMode(request.sslModeOrDefault())
                .build();
        connection = connectionRepository.saveAndFlush(connection);
        log.info("User {} added connection {} ({}:{}/{})", userId, connection.getId(),
                connection.getHost(), connection.getPort(), connection.getDatabaseName());
        return ConnectionResponse.from(connection);
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list() {
        UUID userId = currentUserService.require().getId();
        return connectionRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ConnectionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConnectionResponse get(UUID id) {
        return ConnectionResponse.from(requireOwned(id));
    }

    @Transactional
    public ConnectionTestResult test(UUID id) {
        DatabaseConnection connection = requireOwned(id);
        String password = secretStore.retrieve(connection.getSecretRef());
        ConnectionTestResult result = prober.probe(connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getUsername(), password, connection.getSslMode());

        connection.setStatus(result.success() ? ConnectionStatus.HEALTHY : ConnectionStatus.UNREACHABLE);
        connection.setLastTestedAt(Instant.now());
        connection.setLastError(result.success() ? null : result.message());
        connectionRepository.save(connection);
        return result;
    }

    public ConnectionTestResult testAdhoc(TestConnectionRequest request) {
        return prober.probe(request.host().trim(), request.port(), request.databaseName().trim(),
                request.username().trim(), request.password(), request.sslModeOrDefault());
    }

    @Transactional
    public void delete(UUID id) {
        DatabaseConnection connection = requireOwned(id);
        secretStore.delete(connection.getSecretRef());
        connectionRepository.delete(connection);
        log.info("User {} deleted connection {}", connection.getUserId(), id);
    }

    private DatabaseConnection requireOwned(UUID id) {
        return connectionAccess.requireOwned(id);
    }
}
