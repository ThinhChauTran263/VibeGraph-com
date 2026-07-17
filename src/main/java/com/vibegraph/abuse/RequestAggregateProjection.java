package com.vibegraph.abuse;

import java.time.Instant;
import java.util.UUID;

public interface RequestAggregateProjection {
    UUID getUserId();
    String getIpAddress();
    String getApiKeyRef();
    Instant getMinuteBucket();
    long getRequestCount();
}
