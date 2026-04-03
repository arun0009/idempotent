package io.github.arun0009.idempotent.nats;

import io.nats.client.Options;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.StorageType;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "idempotent.nats")
class NatsIdempotentProperties {

    /** Enable or disable nats client configuration. */
    private boolean enable = true;

    /** Add an array of servers to the list of known servers. */
    private List<String> servers = List.of(Options.DEFAULT_URL);

    /**
     * Turn on verbose mode with the server and Enable connection trace messages. Messages are printed
     * to standard out. This option is for very fine-grained debugging of connection issues
     */
    private boolean verbose = false;

    /** Set the interval between attempts to ping the server. */
    private Duration pingInterval = Options.DEFAULT_PING_INTERVAL;

    /** Set the maximum number of reconnect attempts. */
    private int maxReconnects = Options.DEFAULT_MAX_RECONNECT;

    /** Set the time to wait between reconnect attempts to the same server. */
    private Duration reconnectWait = Options.DEFAULT_RECONNECT_WAIT;

    /** Set the timeout for connection attempts. */
    private Duration connectionTimeout = Options.DEFAULT_CONNECTION_TIMEOUT;

    private final @Nullable AuthUser auth;

    private final BucketConfig bucketConfig;

    NatsIdempotentProperties(
            @Nullable Boolean enable,
            @Nullable List<String> servers,
            @Nullable Boolean verbose,
            @Nullable Duration pingInterval,
            @Nullable Integer maxReconnects,
            @Nullable Duration reconnectWait,
            @Nullable Duration connectionTimeout,
            @Nullable AuthUser auth,
            @DefaultValue BucketConfig bucketConfig) {
        if (enable != null) this.enable = enable;
        if (servers != null) this.servers = servers;
        if (verbose != null) this.verbose = verbose;
        if (pingInterval != null) this.pingInterval = pingInterval;
        if (maxReconnects != null) this.maxReconnects = maxReconnects;
        if (reconnectWait != null) this.reconnectWait = reconnectWait;
        if (connectionTimeout != null) this.connectionTimeout = connectionTimeout;
        this.auth = auth;
        this.bucketConfig = bucketConfig;
    }

    BucketConfig getBucketConfig() {
        return bucketConfig;
    }

    /** Build Nats options */
    Options.Builder toOptions() {
        var builder = new Options.Builder()
                .servers(servers.toArray(new String[0]))
                .connectionTimeout(connectionTimeout)
                .pingInterval(pingInterval)
                .maxReconnects(maxReconnects)
                .reconnectWait(reconnectWait);
        if (verbose) builder.verbose().traceConnection();
        if (auth != null) {
            if (auth.type() == AuthUser.Type.TOKEN) {
                builder.token(auth.token());
            } else if (auth.type() == AuthUser.Type.BASIC) {
                builder.userInfo(auth.username(), auth.password());
            }
        }

        return builder;
    }

    public boolean isEnable() {
        return enable;
    }

    /**
     * Authentication properties. Options can't have both a token and username/password, exactly one
     * type of authentication must be provided.
     */
    record AuthUser(
            @Nullable Type type, char @Nullable [] username, char @Nullable [] password, char @Nullable [] token) {

        AuthUser {
            if (type == Type.BASIC) {
                Assert.notNull(username, "username cannot be null");
                Assert.notNull(password, "password cannot be null");
            } else if (type == Type.TOKEN) {
                Assert.notNull(token, "token cannot be null");
            }
        }

        enum Type {
            BASIC,
            TOKEN
        }
    }

    record BucketConfig(
            @DefaultValue("idempotent") String name,
            @DefaultValue("P1D") Duration ttl,
            @DefaultValue("PT1S") Duration limitMarker,
            @DefaultValue("Memory") StorageType storageType) {

        KeyValueConfiguration.Builder toOptions() {
            return new KeyValueConfiguration.Builder()
                    .name(name)
                    .maxHistoryPerKey(1)
                    .description("Idempotent bucket configuration")
                    .ttl(ttl)
                    .limitMarker(limitMarker)
                    .storageType(storageType);
        }
    }
}
