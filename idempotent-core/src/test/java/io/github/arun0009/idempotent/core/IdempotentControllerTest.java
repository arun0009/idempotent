package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.core")
@SpringBootTest
class IdempotentControllerTest {
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private static final ThreadLocal<Integer> counter = new ThreadLocal<>();

    @BeforeAll
    static void beforeClass() {
        counter.set(1); // Initialize counter before tests
    }

    @AfterAll
    static void afterClass() {
        counter.remove(); // Remove ThreadLocal after tests
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(
                        mockMvc, repetitionInfo.getCurrentRepetition(), "Create", post("/in-memory/assets"));
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void updateAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(
                        mockMvc, repetitionInfo.getCurrentRepetition(), "Update", put("/in-memory/assets"));
    }

    @Test
    void updateAssetError() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(put("/in-memory/assets-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        // language=json
                        .content("""
                        {
                             "id": 1111,
                             "type": {
                                 "category": "error test API",
                                 "version" : "1"
                              },
                             "name": "Asset API-1"
                         }
                        """))
                .andReturn()
                .getResponse();

        assertEquals(404, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());

        Map<String, String> error = assertDoesNotThrow(
                () -> JsonMapper.shared().readValue(response.getContentAsByteArray(), new TypeReference<>() {}));
        assertNotNull(error);
        assertEquals("1111", error.get("id"));
        assertEquals("404", error.get("status"));
        assertEquals("/in-memory/assets-error", error.get("instance"));
        assertEquals("Ops... Asset not found!", error.get("detail"));
    }
}
