package io.github.arun0009.idempotent.rds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class RdsCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(RdsCleanupTask.class);
    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final RdsDialect dialect;
    private final int batchSize;

    public RdsCleanupTask(JdbcTemplate jdbcTemplate, String tableName, RdsDialect dialect, int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.dialect = dialect;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${idempotent.rds.cleanup.fixedDelay:60000}")
    public void cleanup() {
        long now = System.currentTimeMillis();
        int totalDeleted = 0;
        int batchDeleted;

        do {
            batchDeleted = deleteBatch(now);
            totalDeleted += batchDeleted;
        } while (batchDeleted > 0 && batchDeleted == batchSize);

        if (totalDeleted > 0) {
            log.debug("Deleted {} expired idempotent keys", totalDeleted);
        }
    }

    private int deleteBatch(long now) {
        String sql;
        if (dialect == RdsDialect.MYSQL) {
            sql = """
                    DELETE FROM %s WHERE expiration_time_millis < ? LIMIT ?
                    """.formatted(tableName);
            return jdbcTemplate.update(sql, now, batchSize);
        } else if (dialect == RdsDialect.POSTGRES) {
            sql = """
                    DELETE FROM %s WHERE ctid IN (SELECT ctid FROM %s WHERE expiration_time_millis < ? LIMIT ?)
                    """.formatted(tableName, tableName);
            return jdbcTemplate.update(sql, now, batchSize);
        } else {
            // Generic or H2 (H2 in MySQL mode supports LIMIT)
            sql = """
                    DELETE FROM %s WHERE expiration_time_millis < ? LIMIT ?
                    """.formatted(tableName);
            return jdbcTemplate.update(sql, now, batchSize);
        }
    }
}
