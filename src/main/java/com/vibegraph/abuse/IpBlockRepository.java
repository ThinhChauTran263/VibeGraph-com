package com.vibegraph.abuse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IpBlockRepository extends JpaRepository<IpBlock, UUID> {

    @Query("SELECT b FROM IpBlock b WHERE b.ipAddress = :ip AND b.active = true "
            + "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    Optional<IpBlock> findActive(@Param("ip") String ip, @Param("now") Instant now);

    List<IpBlock> findAllByOrderByActiveDescUpdatedAtDesc(Pageable pageable);
}
