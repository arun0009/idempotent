package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.service.IdempotentScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = IdempotentScopeIntegrationTest.Config.class)
class IdempotentScopeIntegrationTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private IdempotentStore store;

    @Test
    void headerKeyIsScopedByAuthenticatedCallerThroughAutoConfiguration() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String user : new String[] {"alice", "bob", "alice", "bob"}) {
            var response = mvc.perform(
                            post("/scoped-orders").principal(() -> user).header("X-Idempotency-Key", "request-123"))
                    .andReturn()
                    .getResponse();
            assertEquals(200, response.getStatus());
            assertEquals(user.equals("alice") ? "alice-1" : "bob-2", response.getContentAsString());
        }
        for (var user : new String[] {"alice", "bob"}) {
            var entry = store.getValue(
                    new IdempotentStore.IdempotentKey(user + "_request-123", "__ScopedController.create()"),
                    String.class);
            assertNotNull(entry);
            assertEquals(IdempotentStore.Status.COMPLETED, entry.status());
        }
    }

    @TestConfiguration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy
    @Import(ScopedController.class)
    static class Config {
        @Bean
        IdempotentScopeResolver scopeResolver() {
            return () -> {
                var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                return requireNonNull(attributes.getRequest().getUserPrincipal())
                        .getName();
            };
        }
    }

    @RestController
    static class ScopedController {
        private final AtomicInteger executions = new AtomicInteger();

        @Idempotent
        @PostMapping("/scoped-orders")
        public String create(Principal principal) {
            return principal.getName() + "-" + executions.incrementAndGet();
        }
    }
}
