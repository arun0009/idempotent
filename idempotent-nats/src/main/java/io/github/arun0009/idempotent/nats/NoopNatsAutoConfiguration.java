package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "idempotent.nats", name = "enable", havingValue = "false")
class NoopNatsAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NoopNatsAutoConfiguration.class);

    @Bean
    IdempotentStore idempotentStore() {
        log.warn("Noop idempotent store activated");
        return new NoopIdempotentStore();
    }

    static class NoopIdempotentStore implements IdempotentStore {

        private NoopIdempotentStore() {}

        @Override
        public Value getValue(IdempotentKey key, Class<?> returnType) {
            return null;
        }

        @Override
        public void store(IdempotentKey key, Value value) {}

        @Override
        public void remove(IdempotentKey key) {}

        @Override
        public void update(IdempotentKey key, Value value) {}
    }
}
