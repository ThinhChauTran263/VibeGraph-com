package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.RequestEvent;

import com.vibegraph.auth.domain.entity.SecurityEvent;

public record PendingRequestEvent(RequestEvent requestEvent, SecurityEvent securityEvent) {
}

