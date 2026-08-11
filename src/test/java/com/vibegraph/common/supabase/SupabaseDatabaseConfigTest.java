package com.vibegraph.common.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Startup validation of the Supabase credential configuration. Every assertion also checks that no
 * credential value leaks into the failure message.
 */
class SupabaseDatabaseConfigTest {

    // Synthetic fixtures, not credentials: used only to assert they never reach a failure message.
    private static final String RUNTIME_PASSWORD = "FIXTURE-NOT-A-REAL-RUNTIME-CREDENTIAL";
    private static final String MIGRATION_PASSWORD = "FIXTURE-NOT-A-REAL-MIGRATION-CREDENTIAL";

    private final SupabaseDatabaseConfig config = new SupabaseDatabaseConfig();

    @Test
    @DisplayName("disabled Supabase reuses the primary datasource and skips validation")
    void supabaseDatabase_disabled_usesPrimaryDataSource() {
        SupabaseProperties properties = new SupabaseProperties();
        properties.setEnabled(false);

        SupabaseDatabase database = config.supabaseDatabase(null, properties);

        assertThat(database.dataSource()).isNull();
        assertThatCode(database::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enabled Supabase without runtime credentials fails fast without echoing a value")
    void supabaseDatabase_missingRuntimeCredentials_failsFast() {
        SupabaseProperties properties = enabled();
        properties.setPassword(null);

        assertThatThrownBy(() -> config.supabaseDatabase(null, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_DB_PASSWORD")
                .hasMessageNotContaining(RUNTIME_PASSWORD);
    }

    @Test
    @DisplayName("an invalid schema name is rejected before any connection is opened")
    void supabaseDatabase_invalidSchema_failsFast() {
        SupabaseProperties properties = enabled();
        properties.setSchema("public; DROP SCHEMA vibegraph_realtime");

        assertThatThrownBy(() -> config.supabaseDatabase(null, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid Supabase schema name");
    }

    @Test
    @DisplayName("require-separate-credentials without a migration credential fails fast")
    void supabaseDatabase_separationRequiredButMigrationMissing_failsFast() {
        SupabaseProperties properties = enabled();
        properties.setRequireSeparateCredentials(true);

        assertThatThrownBy(() -> config.supabaseDatabase(null, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_MIGRATION_DB_URL")
                .hasMessageNotContaining(RUNTIME_PASSWORD)
                .hasMessageNotContaining(MIGRATION_PASSWORD);
    }

    @Test
    @DisplayName("require-separate-credentials rejects the same role on the same database")
    void supabaseDatabase_separationRequiredButSameRole_failsFast() {
        SupabaseProperties properties = enabled();
        properties.setRequireSeparateCredentials(true);
        properties.getMigration().setJdbcUrl(properties.getJdbcUrl());
        properties.getMigration().setUsername(properties.getUsername());
        properties.getMigration().setPassword(MIGRATION_PASSWORD);

        assertThatThrownBy(() -> config.supabaseDatabase(null, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same role on the same database")
                .hasMessageNotContaining(RUNTIME_PASSWORD)
                .hasMessageNotContaining(MIGRATION_PASSWORD);
    }

    @Test
    @DisplayName("a distinct migration role satisfies the separation requirement")
    void validate_separationRequiredWithDistinctRole_passes() {
        SupabaseProperties properties = enabled();
        properties.setRequireSeparateCredentials(true);
        properties.getMigration().setJdbcUrl(properties.getJdbcUrl());
        properties.getMigration().setUsername("vibegraph_migration");
        properties.getMigration().setPassword(MIGRATION_PASSWORD);

        // Validation runs before any pool is built, so reaching a connection failure means the
        // credential configuration itself was accepted.
        assertThatThrownBy(() -> config.supabaseDatabase(null, properties))
                .isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("migration credentials are only considered configured when all three are present")
    void migration_isConfigured_requiresUrlUserAndPassword() {
        SupabaseProperties.Migration migration = new SupabaseProperties.Migration();
        assertThat(migration.isConfigured()).isFalse();

        migration.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
        assertThat(migration.isConfigured()).isFalse();

        migration.setUsername("vibegraph_migration");
        assertThat(migration.isConfigured()).isFalse();

        migration.setPassword(MIGRATION_PASSWORD);
        assertThat(migration.isConfigured()).isTrue();

        migration.setPassword("   ");
        assertThat(migration.isConfigured()).isFalse();
    }

    private SupabaseProperties enabled() {
        SupabaseProperties properties = new SupabaseProperties();
        properties.setEnabled(true);
        properties.setJdbcUrl("jdbc:postgresql://localhost:1/postgres");
        properties.setUsername("vibegraph_runtime");
        properties.setPassword(RUNTIME_PASSWORD);
        return properties;
    }
}
