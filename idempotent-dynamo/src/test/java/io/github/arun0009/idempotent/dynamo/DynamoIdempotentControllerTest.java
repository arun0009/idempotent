package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.IdempotentTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@ContextConfiguration(classes = DynamoTestConfig.class, initializers = DynamoTestConfig.Initializer.class)
class DynamoIdempotentControllerTest {

    private MockMvc mockMvc;
    private RestTestClient client;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        client = RestTestClient.bindToApplicationContext(webApplicationContext).build();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(
                        mockMvc, repetitionInfo.getCurrentRepetition(), "Create", post("/dynamo/assets"));
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void updateAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(mockMvc, repetitionInfo.getCurrentRepetition(), "Update", put("/dynamo/assets"));
    }

    @Test
    void patchAssetCachesResponseEntity() {
        // language=json
        var assetJson = """
                { "id": "patch-1", "type": { "category": "Patch API", "version": "1" }, "name": "Asset Patch-1" }
                """;

        var first = client.patch()
                .uri("/dynamo/assets/patch-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(assetJson)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("X-Trace", "patch-1")
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        client.patch()
                .uri("/dynamo/assets/patch-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(assetJson)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("X-Trace", "patch-1")
                .expectBody(String.class)
                .isEqualTo(first);
    }
}
