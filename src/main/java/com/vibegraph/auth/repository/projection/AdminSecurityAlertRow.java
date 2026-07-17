package com.vibegraph.auth.repository.projection;

import java.time.Instant;

public interface AdminSecurityAlertRow {
    String getType();
    String getSeverity();
    Long getValue();
    Instant getCreatedAt();
}
