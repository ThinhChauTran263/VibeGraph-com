package com.vibegraph.auth.repository.projection;

public interface AdminStorageSubjectRow {
    String getId();
    String getName();
    String getOwnerEmail();
    Long getUsedBytes();
}
