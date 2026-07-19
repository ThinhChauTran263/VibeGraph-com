package com.vibegraph.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_type", length = 80)
    private String targetType;

    @Column(name = "target_id", length = 160)
    private String targetId;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Builder.Default
    @Column(name = "details", nullable = false, length = 4000)
    private String details = "{}";

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
