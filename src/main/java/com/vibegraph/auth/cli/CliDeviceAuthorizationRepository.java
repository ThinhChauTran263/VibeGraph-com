package com.vibegraph.auth.cli;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Locked reads used to make approval and credential exchange single-use. */
public interface CliDeviceAuthorizationRepository extends JpaRepository<CliDeviceAuthorization, UUID> {

    long deleteByExpiresAtBefore(Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CliDeviceAuthorization a WHERE a.id = :id")
    Optional<CliDeviceAuthorization> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CliDeviceAuthorization a WHERE a.deviceCodeHash = :hash")
    Optional<CliDeviceAuthorization> findByDeviceCodeHashForUpdate(@Param("hash") String hash);
}
