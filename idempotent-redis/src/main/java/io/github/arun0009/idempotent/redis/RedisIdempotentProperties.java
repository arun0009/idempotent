package io.github.arun0009.idempotent.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotent.redis")
public record RedisIdempotentProperties(
        @DefaultValue Standalone standalone,
        @DefaultValue Auth auth,
        @DefaultValue Ssl ssl,
        @DefaultValue Cluster cluster,
        @DefaultValue Sentinel sentinel) {

    public record Standalone(@DefaultValue("") String host) {}

    public record Auth(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String username,
            @DefaultValue("") String password) {}

    public record Ssl(@DefaultValue("false") boolean enabled) {}

    public record Cluster(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String hosts) {}

    public record Sentinel(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String master,
            @DefaultValue("") String nodes) {}
}
