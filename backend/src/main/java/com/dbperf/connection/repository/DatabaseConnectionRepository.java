package com.dbperf.connection.repository;

import com.dbperf.connection.domain.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, UUID> {

    List<DatabaseConnection> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<DatabaseConnection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
