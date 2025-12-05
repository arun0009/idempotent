package io.github.arun0009.idempotent.redis;

import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@FunctionalInterface
public interface IdempotentJacksonJsonBuilderCustomizer {

  /**
   * Customizes the provided GenericJacksonJsonRedisSerializerBuilder instance. For example, this
   * can be used to register additional modules, serialization settings, and type validators.
   *
   * @param builder the GenericJacksonJsonRedisSerializerBuilder to be customized
   */
  void customize(
      GenericJacksonJsonRedisSerializer.GenericJacksonJsonRedisSerializerBuilder<JsonMapper.Builder>
          builder);
}
