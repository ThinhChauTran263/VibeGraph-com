package com.vibegraph.abuse;

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

@Entity
@Table(name = "request_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "api_key_ref", length = 120)
    private String apiKeyRef;

    @Column(name = "ip_address", nullable = false, length = 120)
    private String ipAddress;

    @Column(name = "route", nullable = false, length = 240)
    private String route;

    @Column(name = "http_method", nullable = false, length = 10)
    private String method;

    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
