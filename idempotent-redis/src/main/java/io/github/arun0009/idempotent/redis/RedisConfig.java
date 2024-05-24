package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.Arrays;

/**
 * Redis Configuration for Idempotent store
 */
@Configuration
public class RedisConfig {

    @Value("${idempotent.redis.host}")
    private String redisHost;

    @Value("${idempotent.redis.auth.enabled:true}")
    private boolean redisAuthEnabled;

    @Value("${idempotent.redis.ssl.enabled:true}")
    private boolean redisSslEnabled;

    @Value("${idempotent.redis.auth.password:}")
    private String redisAuthPassword;

    @Value("${idempotent.redis.cluster.enabled: true}")
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
                    new RedisClusterConfiguration(Arrays.asList(redisHost.split(",")));
            if (redisAuthEnabled) {
                redisClusterConfiguration.setUsername("default");
                redisClusterConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory =
                    new JedisConnectionFactory(redisClusterConfiguration, jedisClientConfiguration.build());
        } else {
            String[] hostPort = redisHost.split(":");
            RedisStandaloneConfiguration redisStandaloneConfiguration =
                    new RedisStandaloneConfiguration(hostPort[0], Integer.parseInt(hostPort[1]));
            if (redisAuthEnabled) {
                redisStandaloneConfiguration.setUsername("default");
                redisStandaloneConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory =
                    new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
        }
        if (jedisConnectionFactory.getPoolConfig() != null) {
            jedisConnectionFactory.getPoolConfig().setMaxIdle(30);
            jedisConnectionFactory.getPoolConfig().setMinIdle(10);
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

        template.setKeySerializer(new GenericJackson2JsonRedisSerializer());
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());

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

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
    }
}
