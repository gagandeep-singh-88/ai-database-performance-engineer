package com.dbperf.privacy.repository;

import com.dbperf.privacy.domain.PrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, UUID> {

    Optional<PrivacySettings> findByUserId(UUID userId);
}
