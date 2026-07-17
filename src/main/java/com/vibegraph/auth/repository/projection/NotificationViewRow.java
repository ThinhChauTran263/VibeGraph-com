package com.vibegraph.auth.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface NotificationViewRow {
    UUID getId();
    UUID getAnnouncementId();
    String getTitle();
    String getBody();
    String getType();
    String getSeverity();
    String getCreatorDisplayName();
    String getCreatorEmail();
    Instant getCreatedAt();
    Boolean getDismissible();
    Instant getReadAt();
    Instant getDismissedAt();
}
