package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PropertiesBindingTest {
    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            properties = {
                    "idempotent.key.header=X-Camel-Key-Interval-Millis",
                    "idempotent.inprogress.max.retries=21",
                    "idempotent.inprogress.retry.initial.intervalMillis=111",
                    "idempotent.inprogress.retry.multiplier=4"
            })
    class CamelCaseBinding {
        @Autowired
        IdempotentProperties idempotentProperties;

        @Test
        void shouldLoadProperties() {
            assertNotNull(idempotentProperties);
            assertEquals("X-Camel-Key-Interval-Millis", idempotentProperties.getKeyHeader());
            assertEquals(21, idempotentProperties.getInprogress().getMaxRetries());
            assertEquals(111, idempotentProperties.getInprogress().getRetryInitialIntervalMillis());
            assertEquals(4, idempotentProperties.getInprogress().getRetryMultiplier());
        }
    }

    @Nested
    @SpringBootTest(
            classes = TestApplication.class,
            properties = {
                    "idempotent.key.header=X-Kebab-Key-Interval-Millis",
                    "idempotent.inprogress.max.retries=33",
                    "idempotent.inprogress.retry.initial.interval-millis=222",
                    "idempotent.inprogress.retry.multiplier=5"
            })
    class KebabCaseBinding {
        @Autowired
        IdempotentProperties idempotentProperties;

        @Test
        void shouldLoadPropertiesWithKebabCase() {
            assertNotNull(idempotentProperties);
            assertEquals("X-Kebab-Key-Interval-Millis", idempotentProperties.getKeyHeader());
            assertEquals(33, idempotentProperties.getInprogress().getMaxRetries());
            assertEquals(222, idempotentProperties.getInprogress().getRetryInitialIntervalMillis());
            assertEquals(5, idempotentProperties.getInprogress().getRetryMultiplier());
        }
    }
}
