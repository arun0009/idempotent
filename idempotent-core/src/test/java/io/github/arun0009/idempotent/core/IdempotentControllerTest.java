package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.core")
@SpringBootTest
public class IdempotentControllerTest {
    @Autowired
    private WebApplicationContext webApplicationContext;

    int i = 1;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset() throws Exception {
        new IdempotentTest().validateAssetResponse(mockMvc, "Create", post("/in-memory/assets"));
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void updateAsset() throws Exception {
        new IdempotentTest().validateAssetResponse(mockMvc, "Update", put("/in-memory/assets"));
    }
}
