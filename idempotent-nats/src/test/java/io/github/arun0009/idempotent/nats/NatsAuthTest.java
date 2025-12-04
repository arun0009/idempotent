package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.testcontainers.utility.DockerImageName.parse;

class NatsAuthTest {
    @Nested
    @SpringBootTest(
            classes = NatsTestApplication.class,
            properties = {
                "idempotent.nats.auth.type=basic",
                "idempotent.nats.auth.username=test",
                "idempotent.nats.auth.password=test"
            })
    class Basic {
        private static final NatsContainer NATS_CONTAINER = new NatsContainer(parse("nats:latest"));

        @Autowired
        private IdempotentService idempotentService;

        @DynamicPropertySource
        static void setProperties(DynamicPropertyRegistry registry) {
            registry.add("idempotent.nats.servers", NATS_CONTAINER::getServerUrl);
        }

        @BeforeAll
        static void setUp() {
            NATS_CONTAINER.withExtraCommand("--user", "test", "--pass", "test", "-D");
            NATS_CONTAINER.start();
        }

        @AfterAll
        static void tearDown() {
            NATS_CONTAINER.stop();
        }

        @Test
        void shouldUseAuthToken() {
            assertThatCode(() -> idempotentService.execute("test-key-auth", () -> "result", ofSeconds(1)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @SpringBootTest(
            classes = NatsTestApplication.class,
            properties = {
                "idempotent.nats.auth.type=token",
                "idempotent.nats.auth.token=s3cr3t",
            })
    class Token {
        private static final NatsContainer NATS_CONTAINER = new NatsContainer(parse("nats:latest"));

        @Autowired
        private IdempotentService idempotentService;

        @DynamicPropertySource
        static void setProperties(DynamicPropertyRegistry registry) {
            registry.add("idempotent.nats.servers", NATS_CONTAINER::getServerUrl);
        }

        @BeforeAll
        static void setUp() {
            NATS_CONTAINER.withExtraCommand("--auth", "s3cr3t", "-D");
            NATS_CONTAINER.start();
        }

        @AfterAll
        static void tearDown() {
            NATS_CONTAINER.stop();
        }

        @Test
        void shouldUseAuthToken() {
            assertThatCode(() -> idempotentService.execute("test-key-auth", () -> "result", ofSeconds(1)))
                    .doesNotThrowAnyException();
        }
    }
}
