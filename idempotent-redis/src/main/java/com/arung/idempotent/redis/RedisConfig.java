package com.arung.idempotent.redis;

import com.arung.idempotent.core.aspect.IdempotentAspect;
import com.arung.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.auth.enabled:true}")
    private boolean redisAuthEnabled;

    @Value("${spring.data.redis.ssl.enabled:true}")
    private boolean redisSslEnabled;

    @Value("${spring.data.redis.auth.password}")
    private String redisAuthPassword;

    @Value("${spring.data.redis.cluster.enabled}")
    private boolean redisClusterEnabled;

    @Bean
    JedisConnectionFactory jedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory;
        JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration = JedisClientConfiguration.builder();
        if (redisSslEnabled) {
            jedisClientConfiguration.useSsl();
        }
        if (redisClusterEnabled) {
            RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration(List.of(String.format("%s:%d", redisHost, redisPort)));
            if (redisAuthEnabled) {
                redisClusterConfiguration.setUsername("default");
                redisClusterConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration, jedisClientConfiguration.build());
        } else {
            RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (redisAuthEnabled) {
                redisStandaloneConfiguration.setUsername("default");
                redisStandaloneConfiguration.setPassword(redisAuthPassword);
            }
            jedisConnectionFactory = new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
        }

        return jedisConnectionFactory;
    }

    @Bean
    public RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> redisTemplate() {
        RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        return template;
    }

    @Bean
    public IdempotentAspect redisIdempotentAspect() {
        return new IdempotentAspect(new RedisIdempotentStore(redisTemplate()));
    }
}
