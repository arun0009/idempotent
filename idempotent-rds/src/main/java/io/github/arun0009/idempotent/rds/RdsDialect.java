package io.github.arun0009.idempotent.rds;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

public enum RdsDialect {
    POSTGRES,
    MYSQL,
    H2,
    GENERIC;

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
                return GENERIC;
            });
        } catch (Exception e) {
            return GENERIC;
        }
    }
}
