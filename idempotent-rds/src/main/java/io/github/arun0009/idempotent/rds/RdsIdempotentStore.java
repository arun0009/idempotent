package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * RDS idempotent store using JdbcTemplate with atomic race condition
 * protection.
 */
public class RdsIdempotentStore implements IdempotentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final JsonMapper jsonMapper;
    private final RdsDialect dialect;

    public RdsIdempotentStore(JdbcTemplate jdbcTemplate, String tableName, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.jsonMapper = jsonMapper;
        this.dialect = RdsDialect.detect(jdbcTemplate);
    }

    @Override
    public Value getValue(IdempotentKey key, Class<?> returnType) {
        String sql = """
                SELECT status, expiration_time_millis, response FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        try {
            Value value = jdbcTemplate.queryForObject(
                    sql,
                    new RowMapper<Value>() {
                        @Override
                        public Value mapRow(ResultSet rs, int rowNum) throws SQLException {
                            try {
                                String status = rs.getString("status");
                                Long expirationTime = rs.getLong("expiration_time_millis");
                                if (rs.wasNull()) {
                                    expirationTime = null;
                                }
                                String responseJson = rs.getString("response");
                                Object response = null;
                                if (responseJson != null) {
                                    response = jsonMapper.readValue(responseJson, returnType);
                                }
                                Value result = new Value(status, expirationTime, response);
                                // Check expiration using JVM time
                                if (result.isExpired()) {
                                    return null; // Treat expired as not found
                                }
                                return result;
                            } catch (JacksonException e) {
                                throw new IdempotentException("Error deserializing response", e);
                            }
                        }
                    },
                    key.key(),
                    key.processName());

            // Additional expiration check
            if (value != null && value.isExpired()) {
                return null;
            }
            return value;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        try {
            String responseJson = value.response() != null ? jsonMapper.writeValueAsString(value.response()) : null;
            long now = System.currentTimeMillis();

            switch (dialect) {
                case POSTGRES -> storePostgres(key, value, responseJson, now);
                case MYSQL -> storeMySQL(key, value, responseJson, now);
                case H2, GENERIC -> storeGeneric(key, value, responseJson, now);
            }

        } catch (JacksonException e) {
            throw new IdempotentException("Error serializing response", e);
        }
    }

    private void storePostgres(IdempotentKey key, Value value, String responseJson, long now) {
        // Postgres: use ON CONFLICT with conditional WHERE clause
        String sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (key_id, process_name)
                DO UPDATE SET
                    status = EXCLUDED.status,
                    expiration_time_millis = EXCLUDED.expiration_time_millis,
                    response = EXCLUDED.response
                WHERE %s.expiration_time_millis < ?
                """.formatted(tableName, tableName);
        jdbcTemplate.update(
                sql,
                key.key(),
                key.processName(),
                value.status(),
                value.expirationTimeInMilliSeconds(),
                responseJson,
                now);
    }

    private void storeMySQL(IdempotentKey key, Value value, String responseJson, long now) {
        // MySQL: use ON DUPLICATE KEY UPDATE with IF() condition
        String insertSql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                status = IF(expiration_time_millis < ?, VALUES(status), status),
                expiration_time_millis = IF(expiration_time_millis < ?, VALUES(expiration_time_millis), expiration_time_millis),
                response = IF(expiration_time_millis < ?, VALUES(response), response)
                """.formatted(tableName);
        jdbcTemplate.update(
                insertSql,
                key.key(),
                key.processName(),
                value.status(),
                value.expirationTimeInMilliSeconds(),
                responseJson,
                now,
                now,
                now);
    }

    private void storeGeneric(IdempotentKey key, Value value, String response, long now) {
        // Generic H2: INSERT first, if duplicate then try UPDATE with WHERE clause
        try {
            String insertSQL = """
                    INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                    """.formatted(tableName);
            jdbcTemplate.update(
                    insertSQL,
                    key.key(),
                    key.processName(),
                    value.status(),
                    value.expirationTimeInMilliSeconds(),
                    response);
        } catch (DuplicateKeyException e) {
            // Key exists, try to update only if expired
            String updateSql = """
                    UPDATE %s SET status = ?, expiration_time_millis = ?, response = ?
                    WHERE key_id = ? AND process_name = ? AND expiration_time_millis < ?
                    """.formatted(tableName);
            jdbcTemplate.update(
                    updateSql,
                    value.status(),
                    value.expirationTimeInMilliSeconds(),
                    response,
                    key.key(),
                    key.processName(),
                    now);
            // IF UPDATE affects 0 rows, key exists but not expired - silent fail
        }
    }

    @Override
    public void remove(IdempotentKey key) {
        String sql = """
                DELETE FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        jdbcTemplate.update(sql, key.key(), key.processName());
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        try {
            String responseJson = value.response() != null ? jsonMapper.writeValueAsString(value.response()) : null;
            // Simple UPDATE - trust the caller's contract that this is for INPROGRESS keys
            String updateSql = """
                    UPDATE %s SET status = ?, expiration_time_millis = ?, response = ?
                    WHERE key_id = ? AND process_name = ?
                    """.formatted(tableName);
            jdbcTemplate.update(
                    updateSql,
                    value.status(),
                    value.expirationTimeInMilliSeconds(),
                    responseJson,
                    key.key(),
                    key.processName());
        } catch (JacksonException e) {
            throw new IdempotentException("Error serializing response", e);
        }
    }
}
