package io.github.arun0009.idempotent.rds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for RDS-based idempotency implementation.
 * <p>
 * These properties control the behavior of the idempotent key storage and
 * cleanup
 * in a relational database system.
 * </p>
 *
 * @param tableName the name of the database table used to store idempotent keys
 * @param cleanup   configuration for the cleanup task that removes expired
 *                  idempotent records
 */
@ConfigurationProperties(prefix = "idempotent.rds")
public record RdsIdempotentProperties(
        @DefaultValue("idempotent") String tableName, Cleanup cleanup) {

    /**
     * Configuration properties for the cleanup task that removes expired idempotent
     * records.
     * <p>
     * The cleanup task runs periodically to delete expired idempotent keys from the
     * database, preventing unbounded growth of the idempotent key table.
     * </p>
     *
     * @param enabled    whether the cleanup task should run
     * @param batchSize  maximum number of expired records to delete in a single
     *                   cleanup operation
     * @param fixedDelay delay in milliseconds between consecutive cleanup task
     *                   executions
     */
    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1000") int batchSize,
            @DefaultValue("60000") long fixedDelay) {}
}
