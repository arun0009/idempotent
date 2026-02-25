package io.github.arun0009.idempotent.rds;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.exception.IdempotentKeyConflictException;
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
        var sql = """
                SELECT status, expiration_time_millis, response FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        try {
            return jdbcTemplate.queryForObject(
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
                                return new Value(status, expirationTime, response);
                            } catch (JacksonException e) {
                                throw new IdempotentException("Error deserializing response", e);
                            }
                        }
                    },
                    key.key(),
                    key.processName());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void store(IdempotentKey key, Value value) {
        try {
            var responseJson = value.response() != null ? jsonMapper.writeValueAsString(value.response()) : null;
            switch (dialect) {
                case POSTGRES -> storePostgres(key, value, responseJson);
                case MYSQL -> storeMySQL(key, value, responseJson);
                case H2, GENERIC -> storeGeneric(key, value, responseJson);
            }
        } catch (JacksonException e) {
            throw new IdempotentException("Error serializing value response", e);
        } catch (DuplicateKeyException e) {
            throw new IdempotentKeyConflictException("Idempotent key already exists in " + dialect, key);
        }
    }

    private void storePostgres(IdempotentKey key, Value value, String responseJson) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql, key.key(), key.processName(), value.status(), value.expirationTimeInMilliSeconds(), responseJson);
    }

    private void storeMySQL(IdempotentKey key, Value value, String responseJson) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql, key.key(), key.processName(), value.status(), value.expirationTimeInMilliSeconds(), responseJson);
    }

    private void storeGeneric(IdempotentKey key, Value value, String response) {
        var sql = """
                INSERT INTO %s (key_id, process_name, status, expiration_time_millis, response) VALUES (?, ?, ?, ?, ?)
                """.formatted(tableName);
        jdbcTemplate.update(
                sql, key.key(), key.processName(), value.status(), value.expirationTimeInMilliSeconds(), response);
    }

    @Override
    public void remove(IdempotentKey key) {
        var sql = """
                DELETE FROM %s WHERE key_id = ? AND process_name = ?
                """.formatted(tableName);
        jdbcTemplate.update(sql, key.key(), key.processName());
    }

    @Override
    public void update(IdempotentKey key, Value value) {
        try {
            var responseJson = value.response() != null ? jsonMapper.writeValueAsString(value.response()) : null;
            // Simple UPDATE - trust the caller's contract that this is for INPROGRESS keys
            var updateSql = """
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
