package io.github.arun0009.idempotent.dynamo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * DynamoDB Local test container.
 * Starts a single DynamoDB Local container for all tests in the JVM.
 * The container is automatically started when the class is loaded and
 * stopped when the JVM shuts down.
 */
@SuppressWarnings("resource")
public final class SharedDynamoContainer {
    private static final Logger log = LoggerFactory.getLogger(SharedDynamoContainer.class);

    public static final GenericContainer<?> DYNAMO_CONTAINER;

    static {
        try {
            log.info("Initializing DynamoDB Local test container...");

            // Initialize the container with the desired image and version
            DYNAMO_CONTAINER = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.2.1"))
                    .withExposedPorts(8000)
                    .waitingFor(Wait.forLogMessage(".*Initializing DynamoDB Local.*\\n", 1)) // optional
                    .withReuse(true);

            DYNAMO_CONTAINER.start();
            log.info(
                    "Starting DynamoDB Local container at {}",
                    DYNAMO_CONTAINER.getHost() + ":" + DYNAMO_CONTAINER.getFirstMappedPort());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start DynamoDB Local container", e);
        }
    }
}
