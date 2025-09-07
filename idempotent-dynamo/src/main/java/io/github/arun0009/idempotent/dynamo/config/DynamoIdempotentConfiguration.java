package io.github.arun0009.idempotent.dynamo.config;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for DynamoDB-based IdempotentService.
 */
@Configuration
public class DynamoIdempotentConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    public IdempotentService idempotentService(IdempotentStore idempotentStore) {
        return new IdempotentService(idempotentStore);
    }
}
