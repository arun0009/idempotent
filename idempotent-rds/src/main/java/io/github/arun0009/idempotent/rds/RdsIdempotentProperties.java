package io.github.arun0009.idempotent.rds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for RDS-based idempotency implementation.
 *
 * @param enabled   whether the RDS auto-configuration is active
 * @param tableName the name of the database table used to store idempotent keys
 * @param cleanup   configuration for the cleanup task that removes expired records
 */
@ConfigurationProperties(prefix = "idempotent.rds")
public record RdsIdempotentProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("idempotent") String tableName,
        @DefaultValue Cleanup cleanup) {

    /**
     * Configuration for the cleanup task that removes expired idempotent records.
     *
     * @param enabled    whether the cleanup task should run
     * @param batchSize  maximum number of expired records to delete per batch
     * @param fixedDelay delay in milliseconds between cleanup executions
     */
    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1000") int batchSize,
            @DefaultValue("60000") long fixedDelay) {}
}
