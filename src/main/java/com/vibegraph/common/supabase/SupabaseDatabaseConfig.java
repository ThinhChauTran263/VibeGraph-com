package com.vibegraph.common.supabase;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Wires the Supabase schema with two separate credentials.
 *
 * <p>The migration credential creates the schema and runs Flyway DDL. The runtime pool uses a
 * credential that only has CRUD on the moved tables, so an application-level compromise cannot
 * change the schema. Local setups may reuse one credential; production opts into the split with
 * {@code vibegraph.supabase.require-separate-credentials=true}, which turns a missing or
 * indistinguishable credential into a startup failure.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SupabaseDatabaseConfig {

    private static final String SCHEMA_PATTERN = "[a-zA-Z_][a-zA-Z0-9_]*";

    @Bean(destroyMethod = "close")
    public SupabaseDatabase supabaseDatabase(DataSource dataSource, SupabaseProperties properties) {
        if (!properties.isEnabled()) {
            return new SupabaseDatabase(dataSource, null);
        }
        validate(properties);
        HikariDataSource runtimeDataSource = pool(
                "vibegraph-supabase-runtime",
                properties.getJdbcUrl(), properties.getUsername(), properties.getPassword(),
                properties.getSchema(), properties.getMaximumPoolSize(),
                properties.getConnectionTimeoutMs());
        return new SupabaseDatabase(runtimeDataSource, runtimeDataSource);
    }

    /**
     * Owns the DDL credential. Not a default candidate, so nothing can pick it up by type and use
     * it for runtime traffic; the context closes it with the application.
     */
    @Bean(name = "supabaseMigrationDatabase", defaultCandidate = false, destroyMethod = "close")
    public SupabaseDatabase supabaseMigrationDatabase(SupabaseProperties properties) {
        if (!properties.isEnabled()) {
            return new SupabaseDatabase(null, null);
        }
        validate(properties);
        SupabaseProperties.Migration migration = properties.getMigration();
        String jdbcUrl = migration.isConfigured() ? migration.getJdbcUrl() : properties.getJdbcUrl();
        String username = migration.isConfigured() ? migration.getUsername() : properties.getUsername();
        String password = migration.isConfigured() ? migration.getPassword() : properties.getPassword();
        if (!migration.isConfigured()) {
            log.warn("Supabase migration and runtime share one credential. Set "
                    + "SUPABASE_MIGRATION_DB_URL/USER/PASSWORD and "
                    + "SUPABASE_REQUIRE_SEPARATE_CREDENTIALS=true before running in production, so the "
                    + "runtime role cannot run DDL.");
        }
        // The schema has to exist before Flyway can point at it, and only the DDL credential may
        // create it.
        HikariDataSource migrationDataSource = pool(
                "vibegraph-supabase-migration", jdbcUrl, username, password,
                null, migration.getMaximumPoolSize(), properties.getConnectionTimeoutMs());
        ensureSchemaExists(migrationDataSource, properties.getSchema());
        return new SupabaseDatabase(migrationDataSource, migrationDataSource);
    }

    @Bean(name = "supabaseJdbcTemplate", defaultCandidate = false)
    public NamedParameterJdbcTemplate supabaseJdbcTemplate(SupabaseDatabase database) {
        return new NamedParameterJdbcTemplate(database.dataSource());
    }

    /**
     * Supabase-scoped transaction manager.
     *
     * <p>Enabled: an isolated {@link DataSourceTransactionManager} over the Supabase pool.
     * Disabled: a delegate onto the primary transaction manager, because the Supabase
     * repositories then share the primary {@code DataSource} with JPA and must not open a
     * competing transaction on it.
     *
     * <p>The bean is deliberately not a default candidate so the JPA auto-configuration keeps
     * providing the application's primary {@code transactionManager}.
     */
    @Bean(name = "supabaseTransactionManager", defaultCandidate = false)
    public PlatformTransactionManager supabaseTransactionManager(
            SupabaseDatabase database,
            SupabaseProperties properties,
            ObjectProvider<PlatformTransactionManager> primaryTransactionManager) {
        if (!properties.isEnabled()) {
            return new PrimaryDelegatingTransactionManager(primaryTransactionManager);
        }
        return new DataSourceTransactionManager(database.dataSource());
    }

    @Bean
    public InitializingBean supabaseFlywayInitializer(
            @org.springframework.beans.factory.annotation.Qualifier("supabaseMigrationDatabase")
            SupabaseDatabase migrationDatabase,
            SupabaseProperties properties) {
        return () -> {
            if (properties.isEnabled()) {
                migrate(migrationDatabase, properties);
            }
        };
    }

    private void migrate(SupabaseDatabase database, SupabaseProperties properties) {
        Flyway.configure()
                .dataSource(database.dataSource())
                .schemas(properties.getSchema())
                .defaultSchema(properties.getSchema())
                .locations("classpath:db/supabase")
                .load()
                .migrate();
    }

    private HikariDataSource pool(String poolName, String jdbcUrl, String username, String password,
            String schema, int maximumPoolSize, long connectionTimeoutMs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        if (schema != null) {
            config.setSchema(schema);
        }
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(connectionTimeoutMs);
        return new HikariDataSource(config);
    }

    /**
     * Fails fast on an unusable credential configuration. Messages name environment variables only:
     * a credential value must never reach a log or an exception.
     */
    private void validate(SupabaseProperties properties) {
        if (!StringUtils.hasText(properties.getJdbcUrl())
                || !StringUtils.hasText(properties.getUsername())
                || !StringUtils.hasText(properties.getPassword())) {
            throw new IllegalStateException(
                    "Supabase is enabled but SUPABASE_DB_URL, SUPABASE_DB_USER, or SUPABASE_DB_PASSWORD is missing");
        }
        if (!properties.getSchema().matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("Invalid Supabase schema name");
        }
        if (!properties.isRequireSeparateCredentials()) {
            return;
        }
        SupabaseProperties.Migration migration = properties.getMigration();
        if (!migration.isConfigured()) {
            throw new IllegalStateException(
                    "vibegraph.supabase.require-separate-credentials is true but "
                            + "SUPABASE_MIGRATION_DB_URL, SUPABASE_MIGRATION_DB_USER, or "
                            + "SUPABASE_MIGRATION_DB_PASSWORD is missing");
        }
        if (migration.getJdbcUrl().equals(properties.getJdbcUrl())
                && migration.getUsername().equals(properties.getUsername())) {
            throw new IllegalStateException(
                    "vibegraph.supabase.require-separate-credentials is true but the migration and "
                            + "runtime credentials are the same role on the same database; the runtime "
                            + "role must not be able to run DDL");
        }
    }

    private void ensureSchemaExists(DataSource dataSource, String schema) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Could not initialize the Supabase schema", ex);
        }
    }
}
