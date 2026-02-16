package io.github.arun0009.idempotent.core;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(IdempotentProperties.class)
@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.core")
class TestApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(TestApplication.class)
                .main(TestApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }
}
