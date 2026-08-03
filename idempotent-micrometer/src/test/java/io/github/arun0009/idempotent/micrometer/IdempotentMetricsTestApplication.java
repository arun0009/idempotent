package io.github.arun0009.idempotent.micrometer;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.micrometer")
class IdempotentMetricsTestApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(IdempotentMetricsTestApplication.class)
                .main(IdempotentMetricsTestApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }

    @Bean
    SimpleMeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @RestController
    static class MetricsController {

        @Idempotent(key = "#request.key", duration = "PT1M")
        @PostMapping("/operations")
        Operation create(@RequestBody CreateOperationRequest request) {
            return new Operation(request.key(), "id-" + UUID.randomUUID());
        }

        @Idempotent(key = "#request.key", duration = "PT1M")
        @PostMapping("/operations/failing")
        Operation failing(@RequestBody CreateOperationRequest request) {
            throw new IllegalStateException("boom");
        }
    }

    record CreateOperationRequest(String key) {}

    record Operation(String key, String id) {}
}
