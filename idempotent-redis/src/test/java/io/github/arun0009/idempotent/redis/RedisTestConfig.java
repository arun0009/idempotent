package io.github.arun0009.idempotent.redis;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.redis")
public class RedisTestConfig {

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("idempotent.redis.standalone.host=" + SharedRedisContainer.REDIS_CONTAINER.getHost()
                            + ":" + SharedRedisContainer.REDIS_CONTAINER.getFirstMappedPort())
                    .applyTo(applicationContext.getEnvironment());
        }
    }
}
