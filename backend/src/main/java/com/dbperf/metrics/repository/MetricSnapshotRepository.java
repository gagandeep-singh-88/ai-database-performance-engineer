package com.dbperf.metrics.repository;

import com.dbperf.metrics.domain.MetricSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, UUID> {

    List<MetricSnapshot> findAllByConnectionIdOrderByCapturedAtDesc(UUID connectionId, Pageable pageable);

    Optional<MetricSnapshot> findFirstByConnectionIdOrderByCapturedAtDesc(UUID connectionId);

    long countByConnectionId(UUID connectionId);
}
