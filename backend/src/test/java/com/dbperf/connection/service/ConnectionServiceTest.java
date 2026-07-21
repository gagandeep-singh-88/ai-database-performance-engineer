package com.dbperf.connection.service;

import com.dbperf.common.exception.DuplicateResourceException;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.ConnectionStatus;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.domain.SslMode;
import com.dbperf.connection.dto.ConnectionResponse;
import com.dbperf.connection.dto.ConnectionTestResult;
import com.dbperf.connection.dto.CreateConnectionRequest;
import com.dbperf.connection.dto.UpdateConnectionRequest;
import com.dbperf.connection.repository.DatabaseConnectionRepository;
import com.dbperf.secrets.SecretStore;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private DatabaseConnectionRepository connectionRepository;
    @Mock
    private SecretStore secretStore;
    @Mock
    private ConnectionProber prober;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ConnectionAccess connectionAccess;

    @InjectMocks
    private ConnectionService connectionService;

    private final UUID userId = UUID.randomUUID();

    private void stubCurrentUser() {
        when(currentUserService.require()).thenReturn(
                User.builder().id(userId).email("jane@example.com").passwordHash("h")
                        .fullName("Jane").role(Role.USER).build());
    }

    private DatabaseConnection ownedConnection(UUID id) {
        return DatabaseConnection.builder()
                .id(id).userId(userId).name("shop").host("db.example.com").port(5432)
                .databaseName("shop").username("monitor").secretRef("local:abc")
                .sslMode(SslMode.PREFER).status(ConnectionStatus.UNKNOWN)
                .build();
    }

    @Test
    void createStoresPasswordInSecretStoreNotInEntity() {
        stubCurrentUser();
        CreateConnectionRequest request = new CreateConnectionRequest(
                "shop", "db.example.com", 5432, "shop", "monitor", "plain-password", null);
        when(connectionRepository.existsByUserIdAndNameIgnoreCase(userId, "shop")).thenReturn(false);
        when(secretStore.store(anyString(), eq("plain-password"))).thenReturn("local:ciphertext");
        when(connectionRepository.saveAndFlush(any(DatabaseConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionResponse response = connectionService.create(request);

        ArgumentCaptor<DatabaseConnection> captor = ArgumentCaptor.forClass(DatabaseConnection.class);
        verify(connectionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSecretRef()).isEqualTo("local:ciphertext");
        assertThat(captor.getValue().getSslMode()).isEqualTo(SslMode.PREFER);
        assertThat(response.name()).isEqualTo("shop");
    }

    @Test
    void createRejectsDuplicateName() {
        stubCurrentUser();
        when(connectionRepository.existsByUserIdAndNameIgnoreCase(userId, "shop")).thenReturn(true);

        assertThatThrownBy(() -> connectionService.create(new CreateConnectionRequest(
                "shop", "h", 5432, "d", "u", "p", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getEnforcesOwnership() {
        UUID foreignId = UUID.randomUUID();
        when(connectionAccess.requireOwned(foreignId))
                .thenThrow(new ResourceNotFoundException("Connection not found"));

        assertThatThrownBy(() -> connectionService.get(foreignId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testUpdatesStatusOnSuccess() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);
        when(secretStore.retrieve("local:abc")).thenReturn("plain-password");
        when(prober.probe("db.example.com", 5432, "shop", "monitor", "plain-password", SslMode.PREFER))
                .thenReturn(new ConnectionTestResult(true, 12, "PostgreSQL 16.4", "48 MB", true, true, "ok"));

        ConnectionTestResult result = connectionService.test(id);

        assertThat(result.success()).isTrue();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.HEALTHY);
        assertThat(connection.getLastError()).isNull();
        assertThat(connection.getLastTestedAt()).isNotNull();
    }

    @Test
    void testUpdatesStatusOnFailure() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);
        when(secretStore.retrieve("local:abc")).thenReturn("plain-password");
        when(prober.probe(any(), eq(5432), any(), any(), any(), any()))
                .thenReturn(ConnectionTestResult.failure(5000, "connection refused"));

        ConnectionTestResult result = connectionService.test(id);

        assertThat(result.success()).isFalse();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.UNREACHABLE);
        assertThat(connection.getLastError()).isEqualTo("connection refused");
    }

    @Test
    void updateKeepsExistingSecretWhenPasswordBlank() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);
        when(connectionRepository.existsByUserIdAndNameIgnoreCase(userId, "shop-renamed")).thenReturn(false);
        when(connectionRepository.save(any(DatabaseConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionResponse response = connectionService.update(id, new UpdateConnectionRequest(
                "shop-renamed", "db2.example.com", 5433, "shop2", "monitor2", "", SslMode.REQUIRE));

        assertThat(response.name()).isEqualTo("shop-renamed");
        assertThat(response.host()).isEqualTo("db2.example.com");
        assertThat(connection.getSecretRef()).isEqualTo("local:abc");
        verify(secretStore, never()).store(anyString(), anyString());
        verify(secretStore, never()).delete(anyString());
    }

    @Test
    void updateRotatesSecretWhenPasswordProvided() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);
        when(secretStore.store(anyString(), eq("new-password"))).thenReturn("local:new-secret");
        when(connectionRepository.save(any(DatabaseConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        connectionService.update(id, new UpdateConnectionRequest(
                "shop", "db.example.com", 5432, "shop", "monitor", "new-password", null));

        verify(secretStore).store(anyString(), eq("new-password"));
        verify(secretStore).delete("local:abc");
        assertThat(connection.getSecretRef()).isEqualTo("local:new-secret");
    }

    @Test
    void setMonitoringTogglesFlag() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);
        when(connectionRepository.save(any(DatabaseConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectionResponse response = connectionService.setMonitoring(id, false);

        assertThat(response.monitoringEnabled()).isFalse();
        assertThat(connection.isMonitoringEnabled()).isFalse();
    }

    @Test
    void deleteRemovesSecretAndRow() {
        UUID id = UUID.randomUUID();
        DatabaseConnection connection = ownedConnection(id);
        when(connectionAccess.requireOwned(id)).thenReturn(connection);

        connectionService.delete(id);

        verify(secretStore).delete("local:abc");
        verify(connectionRepository).delete(connection);
    }
}
