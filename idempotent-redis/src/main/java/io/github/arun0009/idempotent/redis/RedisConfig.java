package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Redis Configuration for Idempotent store.
 */
@Configuration
public class RedisConfig {

    @Value("${idempotent.redis.standalone.host:}")
    private String redisHost;

    @Value("${idempotent.redis.auth.enabled:false}")
    private boolean redisAuthEnabled;

    @Value("${idempotent.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    @Value("${idempotent.redis.auth.username:}")
    private String redisAuthUsername;

    @Value("${idempotent.redis.auth.password:}")
    private String redisAuthPassword;

    @Value("${idempotent.redis.cluster.enabled:false}")
    private boolean redisClusterEnabled;

    @Value("${idempotent.redis.cluster.hosts")
    private String clusterHosts;

    @Value("${idempotent.redis.sentinel.enabled:false}")
    private boolean redisSentinelEnabled;

    @Value("${idempotent.redis.sentinel.master:}")
    private String redisSentinelMaster;

    @Value("${idempotent.redis.sentinel.nodes:}")
    private String redisSentinelNodes;

    /**
     * Jedis connection factory to connect to Redis standalone, cluster, or sentinel instance.
     *
     * @return the jedis connection factory
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(JedisConnectionFactory.class)
    public JedisConnectionFactory jedisConnectionFactory() {
        JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration =
                JedisClientConfiguration.builder();
        if (redisSslEnabled) {
            jedisClientConfiguration.useSsl();
        }

        JedisConnectionFactory jedisConnectionFactory;
        if (redisClusterEnabled) {
            jedisConnectionFactory = redisClusteredConnection(jedisClientConfiguration);
        } else if (redisSentinelEnabled) {
            jedisConnectionFactory = redisSentinelConnection(jedisClientConfiguration);
        } else {
            jedisConnectionFactory = redisStandaloneConnection(jedisClientConfiguration);
        }

        if (jedisConnectionFactory.getPoolConfig() != null) {
            jedisConnectionFactory.getPoolConfig().setMaxIdle(30);
            jedisConnectionFactory.getPoolConfig().setMinIdle(10);
        }

        return jedisConnectionFactory;
    }

    private JedisConnectionFactory redisClusteredConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration) {
        RedisClusterConfiguration redisClusterConfiguration =
                new RedisClusterConfiguration(Arrays.asList(clusterHosts.split(",")));
        if (redisAuthEnabled) {
            redisClusterConfiguration.setUsername(redisAuthUsername);
            redisClusterConfiguration.setPassword(redisAuthPassword);
        }
        return new JedisConnectionFactory(redisClusterConfiguration, jedisClientConfiguration.build());
    }

    private JedisConnectionFactory redisSentinelConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration) {
        if (redisSentinelMaster.isEmpty() || redisSentinelNodes.isEmpty()) {
            throw new IdempotentException(
                    "Both idempotent.redis.sentinel.master and idempotent.redis.sentinel.nodes must be configured for sentinel setup");
        }

        List<RedisNode> nodes = new ArrayList<>();

        for (String node : redisSentinelNodes.split(",")) {
            String[] hostPort = node.split(":");
            nodes.add(new RedisNode(hostPort[0], Integer.parseInt(hostPort[1])));
        }

        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration().master(redisSentinelMaster);
        sentinelConfig.setSentinels(nodes);

        if (redisAuthEnabled) {
            sentinelConfig.setUsername(redisAuthUsername);
            sentinelConfig.setPassword(redisAuthPassword);
        }

        return new JedisConnectionFactory(sentinelConfig, jedisClientConfiguration.build());
    }

    private JedisConnectionFactory redisStandaloneConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration) {
        String[] hostPort = redisHost.split(":");
        if (hostPort.length != 2) {
            throw new IdempotentException("idempotent.redis.host must be in the format host:port");
        }

        RedisStandaloneConfiguration redisStandaloneConfiguration =
                new RedisStandaloneConfiguration(hostPort[0], Integer.parseInt(hostPort[1]));
        if (redisAuthEnabled) {
            redisStandaloneConfiguration.setUsername(redisAuthUsername);
            redisStandaloneConfiguration.setPassword(redisAuthPassword);
        }
        return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
    }

    /**
     * Redis template.
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
     * Create a Redis based Idempotent Store.
     *
     * @param redisTemplate the redis template
     * @return Redis IdempotentStore
     */
    @Bean
    public IdempotentStore redisIdempotentStore(
            RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> redisTemplate) {
        return new RedisIdempotentStore(redisTemplate);
    }

    /**
     *
     * @param idempotentStore idempotent store to use
     * @return IdempotentAspect
     */
    @Bean
    public IdempotentAspect idempotentAspect(IdempotentStore idempotentStore) {
        return new IdempotentAspect(idempotentStore);
    }
}
