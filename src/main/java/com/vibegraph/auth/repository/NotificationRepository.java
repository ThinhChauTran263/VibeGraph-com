package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.UserNotification;
import com.vibegraph.auth.repository.projection.NotificationViewRow;

public interface NotificationRepository extends JpaRepository<UserNotification, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO user_notifications (id, user_id, announcement_id, created_at)
            SELECT gen_random_uuid(), :userId, a.id, a.created_at
            FROM announcements a
            WHERE a.active = true
              AND (a.target = 'ALL' OR a.target = :roleTarget)
              AND (a.starts_at IS NULL OR a.starts_at <= :now)
              AND (a.ends_at IS NULL OR a.ends_at > :now)
            ON CONFLICT (user_id, announcement_id) DO NOTHING
            """, nativeQuery = true)
    int materializeActiveForUser(
            @Param("userId") UUID userId,
            @Param("roleTarget") String roleTarget,
            @Param("now") Instant now);

    @Query(value = """
            SELECT n.id AS "id",
                   a.id AS "announcementId",
                   a.title AS "title",
                   a.body AS "body",
                   a.type AS "type",
                   a.severity AS "severity",
                   creator.display_name AS "creatorDisplayName",
                   creator.email AS "creatorEmail",
                   a.created_at AS "createdAt",
                   a.dismissible AS "dismissible",
                   n.read_at AS "readAt",
                   n.dismissed_at AS "dismissedAt"
            FROM user_notifications n
            JOIN announcements a ON a.id = n.announcement_id
            LEFT JOIN users creator ON creator.id = a.created_by_user_id
            WHERE n.user_id = :userId
              AND n.dismissed_at IS NULL
              AND a.active = true
              AND (a.starts_at IS NULL OR a.starts_at <= :now)
              AND (a.ends_at IS NULL OR a.ends_at > :now)
            ORDER BY a.created_at DESC, n.id DESC
            """, nativeQuery = true)
    List<NotificationViewRow> findActiveForUser(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            Pageable pageable);

    @Query(value = """
            SELECT n.id AS "id",
                   a.id AS "announcementId",
                   a.title AS "title",
                   a.body AS "body",
                   a.type AS "type",
                   a.severity AS "severity",
                   creator.display_name AS "creatorDisplayName",
                   creator.email AS "creatorEmail",
                   a.created_at AS "createdAt",
                   a.dismissible AS "dismissible",
                   n.read_at AS "readAt",
                   n.dismissed_at AS "dismissedAt"
            FROM user_notifications n
            JOIN announcements a ON a.id = n.announcement_id
            LEFT JOIN users creator ON creator.id = a.created_by_user_id
            WHERE n.id = :id AND n.user_id = :userId
            """, nativeQuery = true)
    Optional<NotificationViewRow> findViewByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = COALESCE(n.readAt, :now) "
            + "WHERE n.id = :id AND n.userId = :userId")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.dismissedAt = COALESCE(n.dismissedAt, :now) "
            + "WHERE n.id = :id AND n.userId = :userId")
    int dismiss(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}
