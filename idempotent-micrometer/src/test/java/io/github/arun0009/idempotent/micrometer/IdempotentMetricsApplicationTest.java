package io.github.arun0009.idempotent.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdempotentMetricsTestApplication.class)
class IdempotentMetricsApplicationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void duplicateRequestsRecordNewSuccessThenHitWithOperationTimer() throws Exception {
        // language=JSON
        var body = """
                {
                  "key": "order-42"
                }
                """;

        mockMvc.perform(post("/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(1.0, executions("new_success").count());
        assertEquals(1.0, executions("hit").count());
        assertEquals(1, operations("success").count());
    }

    @Test
    void failedRequestRecordsNewFailureWithTimer() throws Exception {
        // language=JSON
        var body = """
                {
                  "key": "order-43"
                }
                """;

        assertThrows(
                Exception.class,
                () -> mockMvc.perform(post("/operations/failing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)));

        assertEquals(1.0, executions("new_failure").count());
        assertEquals(1, operations("failure").count());
    }

    private io.micrometer.core.instrument.Counter executions(String outcome) {
        return requireNonNull(meterRegistry
                .find("idempotent.executions")
                .tag("outcome", outcome)
                .counter());
    }

    private io.micrometer.core.instrument.Timer operations(String outcome) {
        return requireNonNull(meterRegistry
                .find("idempotent.operation")
                .tag("outcome", outcome)
                .timer());
    }
}
