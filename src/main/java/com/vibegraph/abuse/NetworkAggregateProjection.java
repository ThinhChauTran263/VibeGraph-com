package com.vibegraph.abuse;

import java.time.Instant;

public interface NetworkAggregateProjection {
    String getIpAddress();
    Instant getMinuteBucket();
    long getTotalRequests();
    long getUniqueUsers();
    long getUniqueApiKeys();
}
