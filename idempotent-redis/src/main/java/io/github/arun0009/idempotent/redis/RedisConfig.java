package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.exception.IdempotentException;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.serialization.IdempotentSerializationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis Configuration for Idempotent store.
 */
@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "idempotent.redis", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RedisIdempotentProperties.class)
public class RedisConfig {

    /**
     * Jedis connection factory to connect to Redis standalone, cluster, or sentinel instance. You can
     * pass your own JedisConnectionFactory with @Bean("IdempotentCache")
     *
     * @return the jedis connection factory
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "IdempotentCache")
    public JedisConnectionFactory jedisConnectionFactory(RedisIdempotentProperties properties) {
        var jedisClientConfiguration = JedisClientConfiguration.builder();
        if (properties.ssl().enabled()) {
            jedisClientConfiguration.useSsl();
        }

        JedisConnectionFactory jedisConnectionFactory;
        if (properties.cluster().enabled()) {
            jedisConnectionFactory = redisClusteredConnection(jedisClientConfiguration, properties);
        } else if (properties.sentinel().enabled()) {
            jedisConnectionFactory = redisSentinelConnection(jedisClientConfiguration, properties);
        } else {
            jedisConnectionFactory = redisStandaloneConnection(jedisClientConfiguration, properties);
        }

        return jedisConnectionFactory;
    }

    private JedisConnectionFactory redisClusteredConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration,
            RedisIdempotentProperties properties) {
        var redisClusterConfiguration = new RedisClusterConfiguration(
                List.of(properties.cluster().hosts().split(",")));
        if (properties.auth().enabled()) {
            redisClusterConfiguration.setUsername(properties.auth().username());
            redisClusterConfiguration.setPassword(properties.auth().password());
        }
        return new JedisConnectionFactory(redisClusterConfiguration, jedisClientConfiguration.build());
    }

    private JedisConnectionFactory redisSentinelConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration,
            RedisIdempotentProperties properties) {
        if (properties.sentinel().master().isEmpty()
                || properties.sentinel().nodes().isEmpty()) {
            throw new IdempotentException(
                    "Both idempotent.redis.sentinel.master and idempotent.redis.sentinel.nodes must be configured for sentinel setup");
        }

        List<RedisNode> nodes = new ArrayList<>();

        for (String node : properties.sentinel().nodes().split(",", -1)) {
            String[] hostPort = node.split(":", -1);
            if (hostPort.length != 2) {
                throw new IdempotentException("Invalid sentinel node: " + node);
            }
            nodes.add(new RedisNode(hostPort[0], Integer.parseInt(hostPort[1])));
        }

        var sentinelConfig =
                new RedisSentinelConfiguration().master(properties.sentinel().master());
        sentinelConfig.setSentinels(nodes);

        if (properties.auth().enabled()) {
            sentinelConfig.setUsername(properties.auth().username());
            sentinelConfig.setPassword(properties.auth().password());
        }

        return new JedisConnectionFactory(sentinelConfig, jedisClientConfiguration.build());
    }

    private JedisConnectionFactory redisStandaloneConnection(
            JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration,
            RedisIdempotentProperties properties) {
        String[] hostPort = properties.standalone().host().split(":", -1);
        if (hostPort.length != 2) {
            throw new IdempotentException("idempotent.redis.standalone.host must be in the format host:port for "
                    + properties.standalone().host());
        }

        var redisStandaloneConfiguration = new RedisStandaloneConfiguration(hostPort[0], Integer.parseInt(hostPort[1]));
        if (properties.auth().enabled()) {
            redisStandaloneConfiguration.setUsername(properties.auth().username());
            redisStandaloneConfiguration.setPassword(properties.auth().password());
        }
        return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
    }

    /** Redis serializer used by idempotent RedisTemplate values and keys. */
    @Bean
    @ConditionalOnMissingBean(name = "idempotentRedisSerializer")
    RedisSerializer<Object> idempotentRedisSerializer(
            IdempotentSerializationProperties properties,
            ObjectProvider<IdempotentJacksonJsonBuilderCustomizer> legacyCustomizerProvider) {
        return switch (properties.strategy()) {
            case JAVA -> new JdkSerializationRedisSerializer();
            case JSON -> {
                var legacyCustomizer = legacyCustomizerProvider.getIfAvailable();
                if (legacyCustomizer != null) {
                    var builder = GenericJacksonJsonRedisSerializer.builder();
                    legacyCustomizer.customize(builder);
                    yield builder.build();
                }
                yield GenericJacksonJsonRedisSerializer.builder()
                        .enableUnsafeDefaultTyping()
                        .build();
            }
        };
    }

    @Bean
    public RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("idempotentRedisSerializer") RedisSerializer<Object> idempotentRedisSerializer) {
        var template = new RedisTemplate<IdempotentStore.IdempotentKey, IdempotentStore.Value>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(idempotentRedisSerializer);
        template.setDefaultSerializer(idempotentRedisSerializer);
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
}
