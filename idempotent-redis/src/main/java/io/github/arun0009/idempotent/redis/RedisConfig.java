package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Redis Configuration for Idempotent store.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    // redis standalone host as "hostname:port" format
    @Value("${idempotent.redis.standalone.host:}")
    private String redisHost;

    // redis auth enabled flag, set to true to enable authentication else false
    @Value("${idempotent.redis.auth.enabled:false}")
    private boolean redisAuthEnabled;

    // redis ssl enabled flag, true to enable ssl, else set to false
    @Value("${idempotent.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    // redis auth username, set only if redis auth enabled
    @Value("${idempotent.redis.auth.username:}")
    private String redisAuthUsername;

    // redis auth password, set only if redis auth enabled
    @Value("${idempotent.redis.auth.password:}")
    private String redisAuthPassword;

    // redis cluster mode enabled flag, set to true if using cluster mode redis
    @Value("${idempotent.redis.cluster.enabled:false}")
    private boolean redisClusterEnabled;

    // cluster hosts list seperated by comma in hostname:port format e.g. host1:6379,host2:6379
    @Value("${idempotent.redis.cluster.hosts:")
    private String clusterHosts;

    // sentinel mode redis flag
    @Value("${idempotent.redis.sentinel.enabled:false}")
    private boolean redisSentinelEnabled;

    // sentinel master host in hostname:port format e.g. host1:6379
    @Value("${idempotent.redis.sentinel.master:}")
    private String redisSentinelMaster;

    // sentinel hosts list seperated by comma in hostname:port format e.g. host1:6379,host2:6379
    @Value("${idempotent.redis.sentinel.nodes:}")
    private String redisSentinelNodes;

    /**
     * Jedis connection factory to connect to Redis standalone, cluster, or sentinel instance. You can
     * pass your own JedisConnectionFactory with @Bean("IdempotentCache")
     *
     * @return the jedis connection factory
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "IdempotentCache")
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
            if (hostPort.length != 2) {
                throw new IdempotentException("Invalid sentinel node: " + node);
            }
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
            throw new IdempotentException("idempotent.redis.host must be in the format host:port for " + redisHost);
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
            RedisConnectionFactory connectionFactory,
            IdempotentJacksonJsonBuilderCustomizer idempotentJacksonJsonBuilderCustomizer) {
        RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJacksonJsonRedisSerializer.GenericJacksonJsonRedisSerializerBuilder<JsonMapper.Builder> builder =
                GenericJacksonJsonRedisSerializer.builder();
        idempotentJacksonJsonBuilderCustomizer.customize(builder);
        GenericJacksonJsonRedisSerializer redisSerializer = builder.build();

        template.setKeySerializer(redisSerializer);
        template.setDefaultSerializer(redisSerializer);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotentJacksonJsonBuilderCustomizer idempotentJacksonJsonBuilderCustomizer() {
        return builder -> {
            log.warn(
                    "Using an unrestricted polymorphic type validator. Without restrictions of the PolymorphicTypeValidator deserialization is vulnerable to arbitrary code execution when reading from untrusted sources.");
            builder.enableUnsafeDefaultTyping();
        };
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
}
