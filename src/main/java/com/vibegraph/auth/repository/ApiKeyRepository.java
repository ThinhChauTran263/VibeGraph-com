package com.vibegraph.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUserId(UUID userId);

    int countByUserIdAndDisabledAtIsNull(UUID userId);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);
}
