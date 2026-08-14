package io.github.arun0009.idempotent.rds;

import java.util.List;
import java.util.regex.Pattern;

final class RdsSchemaStatements {

    // Unquoted identifier, optionally catalog- or schema-qualified.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*){0,2}");

    private RdsSchemaStatements() {}

    static List<String> ddl(RdsDialect dialect, String tableName) {
        var indexName = "idx_%s_expires_at".formatted(tableName.replace('.', '_'));
        return switch (dialect) {
            // MySQL has no portable CREATE INDEX IF NOT EXISTS; TEXT is 64KB.
            case MYSQL -> List.of("""
                    CREATE TABLE IF NOT EXISTS %s (
                        key_id VARCHAR(255) NOT NULL,
                        process_name VARCHAR(255) NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        response MEDIUMTEXT,
                        PRIMARY KEY (key_id, process_name),
                        KEY %s (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""".formatted(tableName, indexName));
            case POSTGRES, H2 ->
                List.of(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                            key_id VARCHAR(255) NOT NULL,
                            process_name VARCHAR(255) NOT NULL,
                            status VARCHAR(50) NOT NULL,
                            expires_at BIGINT NOT NULL,
                            response TEXT,
                            PRIMARY KEY (key_id, process_name)
                        )""".formatted(tableName),
                        "CREATE INDEX IF NOT EXISTS %s ON %s (expires_at)".formatted(indexName, tableName));
            case GENERIC -> throw new IllegalArgumentException("No DDL for unrecognized database: " + tableName);
        };
    }

    static String validateIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "idempotent.rds.table-name is not a valid SQL identifier: " + identifier);
        }
        return identifier;
    }
}
