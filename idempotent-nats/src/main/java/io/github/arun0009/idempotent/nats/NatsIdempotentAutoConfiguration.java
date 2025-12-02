package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import io.nats.client.*;
import io.nats.client.api.KeyValueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.time.Instant;

/** Configuration for Nats-based IdempotentService. */
@AutoConfiguration
@EnableConfigurationProperties({NatsIdempotentProperties.class})
@ConditionalOnProperty(prefix = "idempotent.nats", name = "enable", matchIfMissing = true)
class NatsIdempotentAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NatsIdempotentAutoConfiguration.class);

    private static boolean existsKv(String name, KeyValueManagement context) throws IOException {
        try {
            context.getStatus(name);
            return true;
        } catch (JetStreamApiException ignored) {
            return false;
        }
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    IdempotentService idempotentService(IdempotentStore idempotentStore) {
        return new IdempotentService(idempotentStore);
    }

    @Bean
    IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
    }

    @Bean
    IdempotentStore idempotentStore(Connection connection, NatsIdempotentProperties properties) {
        try {
            NatsIdempotentProperties.BucketConfig bucketConfig = properties.getBucketConfig();
            KeyValueManagement context = connection.keyValueManagement();
            KeyValueConfiguration config = bucketConfig.toOptions().build();
            if (existsKv(config.getBucketName(), context)) {
                context.update(config);
            } else {
                context.create(config);
            }

            KeyValueOptions options = KeyValueOptions.builder().build();
            KeyValue keyValue = connection.keyValue(config.getBucketName(), options);
            return new NatsIdempotentStore(keyValue);
        } catch (JetStreamApiException | IOException e) {
            throw new NatsIdempotentExceptions("Error while creating and configuring KV", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    Connection connection(
            NatsIdempotentProperties natsProperties, SslBundles sslBundles, ConnectionListener connectionListener) {
        try {
            Options.Builder builder = natsProperties.toOptions().connectionListener(connectionListener);
            if (sslBundles.getBundleNames().contains("nats-client")) {
                log.info("Using SSL for NATS client");
                builder.sslContext(sslBundles.getBundle("nats-client").createSslContext());
            }
            Connection connection = Nats.connect(builder.build());
            log.info("Connected to NATS server at {}", connection.getConnectedUrl());
            log.atDebug().log(() -> "Nats " + connection.getServerInfo());
            return connection;
        } catch (IOException e) {
            throw new NatsIdempotentExceptions("Error while creating connection", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsIdempotentExceptions("Error while creating connection", e);
        }
    }

    @Bean
    ConnectionListener connectionListener() {
        return new ConnectionListener() {
            public void connectionEvent(Connection conn, Events type) {
                // deprecated
            }

            @Override
            public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
                log.atDebug().log(() ->
                        "Nats connection %s event at %s to %s".formatted(type, Instant.ofEpochMilli(time), uriDetails));
            }
        };
    }
}
