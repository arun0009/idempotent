package io.github.arun0009.idempotent.nats;

import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = NatsTestApplication.class)
@TestPropertySource(properties = {"idempotent.nats.enable=false"})
class NoopNatsTest {
    @Autowired
    private IdempotentStore idempotentStore;

    @Test
    void loadContext() {
        assertNotNull(idempotentStore);
        assertNull(idempotentStore.getValue(new IdempotentStore.IdempotentKey("test-key", "default"), String.class));
        assertInstanceOf(InMemoryIdempotentStore.class, idempotentStore);
    }
}
