package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(
        classes = TestApplication.class,
        properties = {
            "idempotent.inprogress.max.retries=2",
            "idempotent.inprogress.retry.initial.intervalMillis=50",
        })
class IdempotentControllerWaitTest {
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void updateAssetWaitError() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        // language=json
        var content = """
                {
                     "id": 1111,
                     "type": {
                         "category": "error test API",
                         "version" : "1"
                      },
                     "name": "Asset API-1"
                 }
                """;

        var firstFuture = executor.submit(() -> mockMvc.perform(put("/in-memory/assets-error-heavy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andReturn()
                .getResponse());

        var secondFuture = executor.submit(() -> mockMvc.perform(put("/in-memory/assets-error-heavy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andReturn()
                .getResponse());

        // one should be an error of time out and one success
        var results = Stream.of(firstFuture.get(), secondFuture.get())
                .sorted(Comparator.comparingInt(MockHttpServletResponse::getStatus))
                .toList();

        var okResponse = results.get(0);
        assertEquals(200, okResponse.getStatus());

        var errorWaitResponse = results.get(1);
        assertEquals(500, errorWaitResponse.getStatus());

        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, errorWaitResponse.getContentType());

        Map<String, String> error = assertDoesNotThrow(() ->
                JsonMapper.shared().readValue(errorWaitResponse.getContentAsByteArray(), new TypeReference<>() {}));

        assertNotNull(error);
        assertEquals("idempotent-wait-exhausted", error.get("code"));
        assertEquals("500", error.get("status"));
        assertEquals("/in-memory/assets-error-heavy", error.get("instance"));
        assertEquals("Operation wait exhausted in progress after multiple retries", error.get("detail"));

        executor.shutdown();
    }
}
