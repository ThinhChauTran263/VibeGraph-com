package com.vibegraph.common.supabase;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

public final class SupabaseDatabase implements AutoCloseable {

    private final DataSource dataSource;
    private final HikariDataSource ownedDataSource;

    public SupabaseDatabase(DataSource dataSource, HikariDataSource ownedDataSource) {
        this.dataSource = dataSource;
        this.ownedDataSource = ownedDataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        if (ownedDataSource != null) {
            ownedDataSource.close();
        }
    }
}

