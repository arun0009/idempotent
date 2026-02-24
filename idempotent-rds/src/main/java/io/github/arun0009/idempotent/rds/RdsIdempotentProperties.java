package io.github.arun0009.idempotent.rds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent.rds")
public record RdsIdempotentProperties(
        /** Name of the database table for idempotent keys. */
        @DefaultValue("idempotent") String tableName, Cleanup cleanup) {

    public record Cleanup(
            /** Whether the cleanup task is enabled. Set to false for CDS or AOT cache runs. */
            @DefaultValue("true") boolean enabled,

            /** Number of expired records to delete per batch. */
            @DefaultValue("1000") int batchSize,

            /** Fixed delay in milliseconds between cleanup runs. */
            @DefaultValue("60000") long fixedDelay) {}
}
