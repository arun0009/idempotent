package io.github.arun0009.idempotent.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis test container.
 * Starts a single Redis container for all tests in the JVM.
 * The container is automatically started when the class is loaded and
 * stopped when the JVM shuts down.
 */
@SuppressWarnings("resource")
public final class SharedRedisContainer {
    private static final Logger log = LoggerFactory.getLogger(SharedRedisContainer.class);

    public static final GenericContainer<?> REDIS_CONTAINER;

    static {
        try {
            log.info("Initializing Redis test container...");

            // Initialize the container with the desired image and version
            REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:8.0.3-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
                    .withLogConsumer(outputFrame ->
                            log.info("[REDIS] {}", outputFrame.getUtf8String().trim()));

            // Start the container
            log.info("Starting Redis container...");
            REDIS_CONTAINER.start();

        } catch (Exception e) {
            throw new RuntimeException("Failed to start Redis container", e);
        }
    }
}
