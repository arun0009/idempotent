package io.github.arun0009.idempotent.redis.config;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Redis-based IdempotentService.
 */
@Configuration
public class RedisIdempotentConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    public IdempotentService idempotentService(IdempotentStore idempotentStore) {
        return new IdempotentService(idempotentStore);
    }
}
