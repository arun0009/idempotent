package io.github.arun0009.idempotent.rds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

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

    public void cleanup() {
        var now = System.currentTimeMillis();
        var totalDeleted = 0;
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
        return switch (dialect) {
            case MYSQL -> {
                String sql = """
                        DELETE FROM %s WHERE expiration_time_millis < ? LIMIT ?
                        """.formatted(tableName);
                yield jdbcTemplate.update(sql, now, batchSize);
            }
            case POSTGRES -> {
                String sql = """
                        DELETE FROM %s WHERE ctid IN (SELECT ctid FROM %s WHERE expiration_time_millis < ? LIMIT ?)
                        """.formatted(tableName, tableName);
                yield jdbcTemplate.update(sql, now, batchSize);
            }
            case H2, GENERIC -> {
                // H2 in MySQL mode supports LIMIT
                String sql = """
                        DELETE FROM %s WHERE expiration_time_millis < ? LIMIT ?
                        """.formatted(tableName);
                yield jdbcTemplate.update(sql, now, batchSize);
            }
        };
    }
}
