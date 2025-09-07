package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.IdempotentTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@ContextConfiguration(classes = DynamoTestConfig.class, initializers = DynamoTestConfig.Initializer.class)
class DynamoIdempotentControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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
}
