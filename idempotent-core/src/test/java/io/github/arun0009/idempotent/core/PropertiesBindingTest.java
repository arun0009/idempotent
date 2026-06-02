package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PropertiesBindingTest {
    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            properties = {
                "idempotent.key.header=X-Camel-Key-Interval",
                "idempotent.inprogress.max.retries=21",
                "idempotent.inprogress.retry.initial.interval=111ms",
                "idempotent.inprogress.retry.multiplier=4"
            })
    class CamelCaseBinding {
        @Autowired
        IdempotentProperties idempotentProperties;

        @Test
        void shouldLoadProperties() {
            assertNotNull(idempotentProperties);
            assertEquals("X-Camel-Key-Interval", idempotentProperties.keyHeader());
            assertEquals(21, idempotentProperties.inprogress().maxRetries());
            assertEquals(
                    Duration.ofMillis(111), idempotentProperties.inprogress().retryInitialInterval());
            assertEquals(4, idempotentProperties.inprogress().retryMultiplier());
        }
    }

    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            properties = {
                "idempotent.key.header=X-Kebab-Key-Interval",
                "idempotent.inprogress.max.retries=33",
                "idempotent.inprogress.retry.initial.interval=222ms",
                "idempotent.inprogress.retry.multiplier=5"
            })
    class KebabCaseBinding {
        @Autowired
        IdempotentProperties idempotentProperties;

        @Test
        void shouldLoadPropertiesWithKebabCase() {
            assertNotNull(idempotentProperties);
            assertEquals("X-Kebab-Key-Interval", idempotentProperties.keyHeader());
            assertEquals(33, idempotentProperties.inprogress().maxRetries());
            assertEquals(
                    Duration.ofMillis(222), idempotentProperties.inprogress().retryInitialInterval());
            assertEquals(5, idempotentProperties.inprogress().retryMultiplier());
        }
    }
}
