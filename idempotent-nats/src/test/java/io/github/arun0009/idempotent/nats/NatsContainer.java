package io.github.arun0009.idempotent.nats;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers implementation for Nats.
 *
 * <p>Exposed ports:
 *
 * <ul>
 *   <li>Nats: 4222
 *   <li>HTTP: 8222
 * </ul>
 */
class NatsContainer extends GenericContainer<NatsContainer> {
    private static final int CLIENT_PORT = 4222;
    private static final int HTTP_MONITOR_PORT = 8222;
    private final WaitAllStrategy waitAllStrategy = new WaitAllStrategy();

    @SuppressWarnings("resource")
    NatsContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DockerImageName.parse("nats"));
        withExposedPorts(CLIENT_PORT, HTTP_MONITOR_PORT);
        setWaitStrategy(waitAllStrategy);
    }

    @Override
    protected void configure() {
        super.configure();
        setupCommandAndEnv();
    }

    String getServerUrl() {
        return "nats://%s:%s".formatted(getHost(), getMappedPort(CLIENT_PORT));
    }

    @SuppressWarnings("resource")
    private void setupCommandAndEnv() {
        withCommand("--js", "-m", HTTP_MONITOR_PORT + "");
        waitAllStrategy.withStrategy(
                Wait.forHttp("/healthz").forPort(HTTP_MONITOR_PORT).forStatusCode(200));
    }
}
