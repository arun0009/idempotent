package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.IdempotentTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
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

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.redis")
@SpringBootTest
@ContextConfiguration(
        initializers = io.github.arun0009.idempotent.redis.RedisIdempotentControllerTest.Initializer.class)
class RedisIdempotentControllerTest {

    MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    static GenericContainer redis = new GenericContainer<>("redis:6.2.6").withExposedPorts(6379);

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // Start container
            redis.start();
            TestPropertyValues.of(
                            "idempotent.redis.standalone.host=" + redis.getHost() + ":" + redis.getFirstMappedPort())
                    .applyTo(context.getEnvironment());
        }
    }

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterAll
    public static void tearDown() {
        redis.stop();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(mockMvc, repetitionInfo.getCurrentRepetition(), "Create", post("/redis/assets"));
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void updateAsset(RepetitionInfo repetitionInfo) throws Exception {
        new IdempotentTest()
                .validateAssetResponse(mockMvc, repetitionInfo.getCurrentRepetition(), "Update", put("/redis/assets"));
    }
}
