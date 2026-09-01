package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.AuthProvider;
import com.vibegraph.auth.domain.entity.UserIdentity;

/**
 * External identity lookups. Created for Phase 1 schema-freeze; the OAuth login flow that
 * consumes {@code findByProviderAndProviderUserId} is a deferred card.
 */
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
