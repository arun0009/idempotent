package io.github.arun0009.idempotent.nats;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

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
    IdempotentStore idempotentStore(
            Connection connection,
            NatsIdempotentProperties properties,
            IdempotentJacksonJsonBuilderCustomizer jsonBuilderCustomizer) {
        try {
            NatsIdempotentProperties.BucketConfig bucketConfig = properties.getBucketConfig();
            KeyValueManagement context = connection.keyValueManagement();
            KeyValueConfiguration config = bucketConfig.toOptions().build();
            if (existsKv(config.getBucketName(), context)) {
                log.info("KV bucket {} already exists, updating...", config.getBucketName());
                context.update(config);
            } else {
                log.info("Creating KV bucket {}", config.getBucketName());
                context.create(config);
            }

            KeyValueOptions options = KeyValueOptions.builder().build();
            KeyValue keyValue = connection.keyValue(config.getBucketName(), options);

            JsonMapper.Builder jsonBuilder = JsonMapper.builder();
            jsonBuilderCustomizer.customize(jsonBuilder);

            return new NatsIdempotentStore(keyValue, jsonBuilder.build());
        } catch (JetStreamApiException | IOException e) {
            throw new NatsIdempotentExceptions("Error while creating and configuring KV", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotentJacksonJsonBuilderCustomizer idempotentJacksonJsonBuilderCustomizer() {
        return builder -> {
            log.warn(
                    "Using an unrestricted polymorphic type validator. Without restrictions of the PolymorphicTypeValidator deserialization is vulnerable to arbitrary code execution when reading from untrusted sources.");
            BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Object.class)
                    .allowIfSubType((ctx, clazz) -> true)
                    .build();
            builder.polymorphicTypeValidator(ptv)
                    .setDefaultTyping(new DefaultTypeResolverBuilder(
                            ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class"));
        };
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
    @ConditionalOnMissingBean
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
