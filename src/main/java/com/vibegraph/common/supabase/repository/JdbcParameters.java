package com.vibegraph.common.supabase.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class JdbcParameters {

    private JdbcParameters() {
    }

    static OffsetDateTime instant(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
