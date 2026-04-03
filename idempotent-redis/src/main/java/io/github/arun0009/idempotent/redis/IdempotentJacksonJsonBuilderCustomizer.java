package io.github.arun0009.idempotent.redis;

import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * @deprecated since 2.4.0, use core {@code IdempotentJsonMapperCustomizer} or
 *     {@code IdempotentPayloadCodec} instead.
 */
@Deprecated(since = "2.4.0", forRemoval = false)
@FunctionalInterface
public interface IdempotentJacksonJsonBuilderCustomizer {

    void customize(
            GenericJacksonJsonRedisSerializer.GenericJacksonJsonRedisSerializerBuilder<JsonMapper.Builder> builder);
}
