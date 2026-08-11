package com.vibegraph.common.supabase;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vibegraph.supabase")
public class SupabaseProperties {

    private boolean enabled;

    /**
     * Runtime credential: CRUD on the moved tables only. It must not be able to run DDL.
     */
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema = "vibegraph_realtime";

    /**
     * Whether migration and runtime must use distinct credentials.
     *
     * <p>Local and development setups default to {@code false} and may reuse one credential; the
     * application logs a warning naming no secret when it does. Production must set this to
     * {@code true}, which makes the migration credential mandatory and rejects a configuration
     * where migration and runtime are the same role on the same database. The requirement is never
     * inferred from a profile name — an operator opts in explicitly.
     */
    private boolean requireSeparateCredentials;

    private final Migration migration = new Migration();

    @Min(1)
    private int maximumPoolSize = 10;

    @Min(250)
    private long connectionTimeoutMs = 10_000;

    private final Telemetry telemetry = new Telemetry();
    private final Retention retention = new Retention();

    /**
     * Migration credential: creates the schema and runs Flyway DDL. Used only during startup and by
     * operator verification, never by the runtime connection pool.
     */
    @Getter
    @Setter
    public static class Migration {

        private String jdbcUrl;
        private String username;
        private String password;

        @Min(1)
        private int maximumPoolSize = 2;

        public boolean isConfigured() {
            return jdbcUrl != null && !jdbcUrl.isBlank()
                    && username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Telemetry {

        @Min(100)
        private int queueCapacity = 10_000;

        @Min(1)
        @Max(5_000)
        private int batchSize = 250;

        @Min(250)
        private long flushIntervalMs = 2_000;

        /** Maximum number of failed batches held for retry before the oldest is abandoned. */
        @Min(1)
        private int retryQueueCapacity = 200;

        /** Write attempts per batch identity before it is bisected or abandoned. */
        @Min(1)
        private int maxAttempts = 4;

        /** First backoff step; doubled per attempt up to {@code maxRetryBackoffMs}. */
        @Min(100)
        private long retryBackoffMs = 1_000;

        @Min(1_000)
        private long maxRetryBackoffMs = 60_000;

        /** Retry batches processed per flush cycle, so retries never starve fresh telemetry. */
        @Min(1)
        private int retryBatchesPerCycle = 2;

        /**
         * Fresh batches processed per flush cycle. The default of 1 caps drain throughput at
         * {@code batchSize / flushIntervalMs}; raise it once the capacity policy has a measured
         * peak arrival rate to size against. It stays bounded so one cycle cannot hold the flush
         * guard indefinitely.
         */
        @Min(1)
        private int freshBatchesPerCycle = 1;

        /** Hard bound on poison-isolation bisecting. */
        @Min(0)
        @Max(10)
        private int maxSplitDepth = 4;

        /**
         * Time budget for the shutdown drain. The container termination grace period must be
         * longer than this, otherwise the process is killed mid-drain and the remaining events
         * are lost.
         */
        @Min(0)
        private long shutdownDrainTimeoutMs = 10_000;

        /** Pause between shutdown drain attempts after a failure, to avoid a tight retry loop. */
        @Min(0)
        private long shutdownRetryPauseMs = 250;
    }

    @Getter
    @Setter
    public static class Retention {

        @Min(1)
        private int requestEventDays = 14;

        @Min(1)
        private int securityEventDays = 180;

        @Min(1)
        private int dismissedNotificationDays = 90;

        @Min(1)
        private int expiredAnnouncementDays = 180;
    }
}

