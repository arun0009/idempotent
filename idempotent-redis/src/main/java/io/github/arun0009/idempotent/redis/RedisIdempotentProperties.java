package io.github.arun0009.idempotent.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent.redis")
public record RedisIdempotentProperties(
        @DefaultValue("true") boolean enabled) {}
