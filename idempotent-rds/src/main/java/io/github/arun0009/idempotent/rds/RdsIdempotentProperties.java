package io.github.arun0009.idempotent.rds;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idempotent.rds")
public class RdsIdempotentProperties {

    /** Name of the database table for idempotent keys. */
    private String tableName = "idempotent";

    private final Cleanup cleanup = new Cleanup();

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public static class Cleanup {

        /**
         * Whether the cleanup task is enabled. Set to false for CDS or AOT cache runs.
         */
        private boolean enabled = true;

        /** Number of expired records to delete per batch. */
        private int batchSize = 1000;

        /** Fixed delay in milliseconds between cleanup runs. */
        private long fixedDelay = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(long fixedDelay) {
            this.fixedDelay = fixedDelay;
        }
    }
}
