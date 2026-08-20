package io.github.arun0009.idempotent.rds;

import com.zaxxer.hikari.HikariDataSource;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import io.github.arun0009.idempotent.core.serialization.JacksonIdempotentPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RdsAutoConfigurationTest {

    private HikariDataSource dataSource;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:autoconfig-%s".formatted(UUID.randomUUID()));
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RdsAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource))
                .withBean(
                        IdempotentPayloadCodec.class,
                        () -> new JacksonIdempotentPayloadCodec(
                                JsonMapper.builder().build()));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void defaultDoesNotCreateTable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RdsIdempotentProperties.class).initializeSchema())
                    .isEqualTo(DatabaseInitializationMode.NEVER);
            assertThatThrownBy(() -> rowCount(context, "idempotent")).isInstanceOf(BadSqlGrammarException.class);
        });
    }

    @Test
    void embeddedCreatesTableOnH2() {
        runner.withPropertyValues("idempotent.rds.initialize-schema=embedded").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(rowCount(context, "idempotent")).isZero();
        });
    }

    @Test
    void alwaysHonorsCustomTableName() {
        runner.withPropertyValues("idempotent.rds.initialize-schema=always", "idempotent.rds.table-name=my_keys")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(rowCount(context, "my_keys")).isZero();
                });
    }

    @Test
    void rejectsInvalidTableName() {
        runner.withPropertyValues("idempotent.rds.table-name=idempotent;DROP TABLE users")
                .run(context -> assertThat(startupFailure(context))
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("is not a valid SQL identifier"));
    }

    private static Throwable startupFailure(AssertableApplicationContext context) {
        return Objects.requireNonNull(context.getStartupFailure(), "expected the context to fail to start");
    }

    private static int rowCount(AssertableApplicationContext context, String tableName) {
        var count = context.getBean(JdbcTemplate.class)
                .queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Integer.class);
        return Objects.requireNonNull(count, "COUNT(*) never returns null");
    }
}
