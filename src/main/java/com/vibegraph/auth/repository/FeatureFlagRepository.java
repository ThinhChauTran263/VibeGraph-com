package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.FeatureFlag;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByKey(String key);

    boolean existsByKey(String key);

    boolean existsByKeyAndEnabledFalse(String key);
}
