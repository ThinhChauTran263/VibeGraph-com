package com.vibegraph.abuse;

import com.vibegraph.auth.domain.SecurityEvent;

public record PendingRequestEvent(RequestEvent requestEvent, SecurityEvent securityEvent) {
}

