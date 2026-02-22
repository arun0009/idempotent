package io.github.arun0009.idempotent.rds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

public enum RdsDialect {
    POSTGRES,
    MYSQL,
    H2,
    GENERIC;

    private static final Logger log = LoggerFactory.getLogger(RdsDialect.class);

    public static RdsDialect detect(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.execute((Connection conn) -> {
                DatabaseMetaData metaData = conn.getMetaData();
                String databaseProductName = metaData.getDatabaseProductName().toLowerCase();
                if (databaseProductName.contains("postgresql")) {
                    return POSTGRES;
                } else if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
                    return MYSQL;
                } else if (databaseProductName.contains("h2")) {
                    return H2;
                }

                log.warn(
                        "Unknown database product '{}', falling back to GENERIC dialect. SQL syntax may not be optimal.",
                        metaData.getDatabaseProductName());
                return GENERIC;
            });
        } catch (Exception e) {
            log.warn("Failed to detect database dialect, falling back to GENERIC", e);
            return GENERIC;
        }
    }
}
