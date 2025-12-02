package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;

import java.time.Duration;

import static org.testcontainers.utility.DockerImageName.parse;

@SpringBootTest(
        classes = NatsTestApplication.class,
        properties = {
            "spring.ssl.bundle.jks.nats-client.protocol=TLSv1.3",
            "spring.ssl.bundle.jks.nats-client.keystore.location=classpath:certs/client/client.p12",
            "spring.ssl.bundle.jks.nats-client.keystore.password=password",
            "spring.ssl.bundle.jks.nats-client.truststore.location=classpath:certs/client/ca.jks",
            "spring.ssl.bundle.jks.nats-client.truststore.password=password"
        })
class NatsSslTest {
    private static final NatsContainer NATS_CONTAINER = new NatsContainer(parse("nats:latest"));

    @Autowired
    private IdempotentService idempotentService;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("idempotent.nats.servers", NATS_CONTAINER::getServerUrl);
    }

    @BeforeAll
    static void setUp() {
        NATS_CONTAINER.withClasspathResourceMapping("certs/nats.conf", "/etc/nats.conf", BindMode.READ_ONLY);
        NATS_CONTAINER.withClasspathResourceMapping("certs/server", "/certs/", BindMode.READ_ONLY);
        NATS_CONTAINER.withExtraCommand("--config", "/etc/nats.conf");
        NATS_CONTAINER.start();
    }

    @AfterAll
    static void tearDown() {
        NATS_CONTAINER.stop();
    }

    @Test
    void test() {
        idempotentService.execute("test-key", () -> "result", Duration.ofSeconds(1));
    }
}
