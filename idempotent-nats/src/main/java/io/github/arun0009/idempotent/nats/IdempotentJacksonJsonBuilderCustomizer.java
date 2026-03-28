package io.github.arun0009.idempotent.nats;

import tools.jackson.databind.json.JsonMapper;

/**
 * @deprecated since 2.4.0, use core {@code IdempotentJsonMapperCustomizer} instead.
 */
@Deprecated(since = "2.4.0", forRemoval = false)
@FunctionalInterface
public interface IdempotentJacksonJsonBuilderCustomizer {

    void customize(JsonMapper.Builder builder);
}
