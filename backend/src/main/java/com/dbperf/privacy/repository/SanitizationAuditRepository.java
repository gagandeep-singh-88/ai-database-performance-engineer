package com.dbperf.privacy.repository;

import com.dbperf.privacy.domain.SanitizationAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SanitizationAuditRepository extends JpaRepository<SanitizationAudit, UUID> {

    List<SanitizationAudit> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
