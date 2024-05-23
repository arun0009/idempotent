package io.github.arun0009.idempotent.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

/**
 * Redis Configuration for Idempotent store
 */
@Configuration
public class RedisConfig {

    @Value("${idempotent.redis.host}")
    private String redisHost;

    @Value("${idempotent.redis.port}")
    private int redisPort;

    @Value("${idempotent.redis.auth.enabled:true}")
    private boolean redisAuthEnabled;

    @Value("${idempotent.redis.ssl.enabled:true}")
    private boolean redisSslEnabled;

    @Value("${idempotent.redis.auth.password}")
    private String redisAuthPassword;

    @Value("${idempotent.redis.cluster.enabled}")
    private boolean redisClusterEnabled;

    /**
     * Jedis connection factory to connect to redis standalone or cluster instance.
     *
     * @return the jedis connection factory
     */
    @Bean
    JedisConnectionFactory jedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory;
        JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration =
                JedisClientConfiguration.builder();
        if (redisSslEnabled) {
            jedisClientConfiguration.useSsl();
        }
        if (redisClusterEnabled) {
            RedisClusterConfiguration redisClusterConfiguration =
                    new RedisClusterConfiguration(List.of(String.format("%s:%d", redisHost, redisPort)));
            if (redisAuthEnabled) {
                redisClusterConfiguration.setUsername("default");
                redisClusterConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory =
                    new JedisConnectionFactory(redisClusterConfiguration, jedisClientConfiguration.build());
        } else {
            RedisStandaloneConfiguration redisStandaloneConfiguration =
                    new RedisStandaloneConfiguration(redisHost, redisPort);
            if (redisAuthEnabled) {
                redisStandaloneConfiguration.setUsername("default");
                redisStandaloneConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory =
                    new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
        }

        return jedisConnectionFactory;
    }

    /**
     * Redis template redis template.
     *
     * @param connectionFactory the connection factory
     * @return the redis template
     */
    @Bean
    public RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure Jackson ObjectMapper using Jackson2ObjectMapperBuilder
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        // Set the key and value serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JsonRedisSerializer<>(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JsonRedisSerializer<>(objectMapper));

        return template;
    }

    /**
     * Create a Redis based Idempotent Store
     *
     * @param redisTemplate the redis template
     * @return Redis IdempotentStore
     */
    @Bean
    public IdempotentStore redisIdempotentStore(
            RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> redisTemplate) {
        return new RedisIdempotentStore(redisTemplate);
    }
}
