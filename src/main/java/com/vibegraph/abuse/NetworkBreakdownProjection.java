package com.vibegraph.abuse;

import java.util.UUID;

public interface NetworkBreakdownProjection {
    String getIpAddress();
    UUID getUserId();
    String getUserDisplayName();
    String getUserEmail();
    String getApiKeyRef();
    long getRequests();
}
