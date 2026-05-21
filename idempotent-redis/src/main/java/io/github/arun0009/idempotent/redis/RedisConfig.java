package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentPayloadCodec;
import io.github.arun0009.idempotent.core.serialization.IdempotentSerializationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis auto-configuration for the Idempotent store.
 *
 * <p>Uses the application's {@link RedisConnectionFactory} (Lettuce, Jedis, or any other
 * Spring Data Redis driver) rather than managing its own connection. Configure Redis
 * via the standard {@code spring.data.redis.*} properties.
 */
@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "idempotent.redis", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RedisIdempotentProperties.class)
public class RedisConfig {

    @Bean(name = "idempotentRedisTemplate")
    @ConditionalOnMissingBean(name = "idempotentRedisTemplate")
    public RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> idempotentRedisTemplate(
            RedisConnectionFactory connectionFactory,
            IdempotentSerializationProperties properties,
            IdempotentPayloadCodec idempotentPayloadCodec,
            @Qualifier("idempotentRedisSerializer") ObjectProvider<RedisSerializer<Object>> idempotentRedisSerializerProvider) {
        var template = new RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value>();
        template.setConnectionFactory(connectionFactory);

        var customSerializer = idempotentRedisSerializerProvider.getIfAvailable();
        if (customSerializer != null) {
            template.setKeySerializer(customSerializer);
            template.setValueSerializer(customSerializer);
            return template;
        }

        switch (properties.strategy()) {
            case JAVA -> {
                var jdkSerializer = new JdkSerializationRedisSerializer();
                template.setKeySerializer(jdkSerializer);
                template.setValueSerializer(jdkSerializer);
            }
            case JSON -> {
                var keySerializer = new IdempotentPayloadRedisSerializer<>(
                        idempotentPayloadCodec, IdempotentStore.IdempotentKey.class);
                var valueSerializer =
                        new IdempotentPayloadRedisSerializer<>(idempotentPayloadCodec, IdempotentStore.Value.class);
                template.setKeySerializer(keySerializer);
                template.setValueSerializer(valueSerializer);
            }
        }
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore redisIdempotentStore(
            @Qualifier("idempotentRedisTemplate") RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> idempotentRedisTemplate) {
        return new RedisIdempotentStore(idempotentRedisTemplate);
    }
}
