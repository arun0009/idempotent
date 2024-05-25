package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.IdempotentTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.dynamo")
@SpringBootTest
@ContextConfiguration(
        initializers = io.github.arun0009.idempotent.dynamo.DynamoIdempotentControllerTest.Initializer.class)
public class DynamoIdempotentControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    int i = 1;

    private MockMvc mockMvc;

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static GenericContainer dynamo = new GenericContainer<>("amazon/dynamodb-local:2.2.1").withExposedPorts(8000);

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // Start container
            dynamo.start();
            TestPropertyValues.of("idempotent.dynamodb.endpoint=" + "http://" + dynamo.getHost() + ":"
                            + dynamo.getFirstMappedPort())
                    .applyTo(context.getEnvironment());
        }
    }

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset() throws Exception {
        new IdempotentTest().validateAssetResponse(mockMvc, "Create", post("/dynamo/assets"));
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void updateAsset() throws Exception {
        new IdempotentTest().validateAssetResponse(mockMvc, "Update", put("/dynamo/assets"));
    }
}
