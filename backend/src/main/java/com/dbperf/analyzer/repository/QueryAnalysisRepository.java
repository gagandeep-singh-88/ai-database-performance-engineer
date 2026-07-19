package com.dbperf.analyzer.repository;

import com.dbperf.analyzer.domain.QueryAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryAnalysisRepository extends JpaRepository<QueryAnalysis, UUID> {

    List<QueryAnalysis> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<QueryAnalysis> findByIdAndUserId(UUID id, UUID userId);
}
