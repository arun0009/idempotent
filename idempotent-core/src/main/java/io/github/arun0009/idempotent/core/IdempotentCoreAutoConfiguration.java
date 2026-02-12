package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(IdempotentProperties.class)
class IdempotentCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentAspect.class)
    IdempotentAspect idempotentAspect(IdempotentStore idempotentStore, IdempotentProperties properties) {
        return new IdempotentAspect(idempotentStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    IdempotentStore idempotentStore() {
        return new InMemoryIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    IdempotentService idempotentProperties(IdempotentStore idempotentStore) {
        return new IdempotentService(idempotentStore);
    }
}
