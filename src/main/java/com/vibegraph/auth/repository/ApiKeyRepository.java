package com.vibegraph.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(String keyPrefix);

    List<ApiKey> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<ApiKey> findByUserId(UUID userId);

    int countByUserIdAndDeletedAtIsNull(UUID userId);

    int countByUserIdAndDisabledAtIsNull(UUID userId);

    Optional<ApiKey> findByUserIdAndProjectIdAndDeletedAtIsNull(UUID userId, String projectId);

    Optional<ApiKey> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    Optional<ApiKey> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
            SELECT id FROM api_keys
            WHERE project_id = :projectId AND deleted_at IS NULL
            FOR UPDATE
            """, nativeQuery = true)
    List<UUID> lockLiveKeysForProject(@Param("projectId") String projectId);

    @Query("""
            SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END
            FROM ApiKey k
            WHERE k.projectId = :projectId AND k.deletedAt IS NULL
              AND k.disabledBy = com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN
            """)
    boolean existsAdminLockedKeyForProject(@Param("projectId") String projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiKey k
            SET k.disabledAt = :disabledAt, k.disabledBy = com.vibegraph.auth.domain.ApiKeyDisabledBy.USER,
                k.disabledReason = NULL, k.lockedBy = NULL
            WHERE k.id = :id AND k.userId = :userId AND k.deletedAt IS NULL
              AND (k.disabledBy IS NULL OR k.disabledBy <> com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN)
            """)
    int disableByOwnerUnlessAdminLocked(
            @Param("id") UUID id, @Param("userId") UUID userId, @Param("disabledAt") java.time.Instant disabledAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiKey k
            SET k.disabledAt = :disabledAt, k.disabledBy = com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN,
                k.disabledReason = :reason, k.lockedBy = :lockedBy
            WHERE k.id = :id AND k.deletedAt IS NULL
            """)
    int disableByAdmin(
            @Param("id") UUID id, @Param("disabledAt") java.time.Instant disabledAt,
            @Param("reason") String reason, @Param("lockedBy") String lockedBy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiKey k
            SET k.disabledAt = NULL, k.disabledBy = NULL, k.disabledReason = NULL, k.lockedBy = NULL
            WHERE k.id = :id AND k.deletedAt IS NULL
              AND k.disabledBy = com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN
            """)
    int unlockByAdmin(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiKey k
            SET k.deletedAt = :deletedAt
            WHERE k.id = :id AND k.userId = :userId AND k.deletedAt IS NULL
              AND (k.disabledBy IS NULL OR k.disabledBy <> com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN)
            """)
    int softDeleteByOwnerUnlessAdminLocked(
            @Param("id") UUID id, @Param("userId") UUID userId, @Param("deletedAt") java.time.Instant deletedAt);
}
