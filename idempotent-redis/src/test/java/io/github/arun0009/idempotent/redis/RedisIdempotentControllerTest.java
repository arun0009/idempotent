package io.github.arun0009.idempotent.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootApplication(scanBasePackages = "io.github.arun0009.idempotent.redis")
@SpringBootTest
@ContextConfiguration(
        initializers = io.github.arun0009.idempotent.redis.RedisIdempotentControllerTest.Initializer.class)
class RedisIdempotentControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    int i = 1;

    private MockMvc mockMvc;

    static GenericContainer redis = new GenericContainer<>("redis:6.2.6").withExposedPorts(6379);

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // Start container
            redis.start();
            TestPropertyValues.of("idempotent.redis.host=" + redis.getHost() + ":" + redis.getFirstMappedPort())
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
    void createAsset() throws Exception {
        String assetJson =
                """
                {
                    "id": 1,
                    "type": "API",
                    "name": "%s"
                }
                """;

        String expectedResponseJson =
                """
                {"id":"1","type":"API","name":"Asset API-1","url":"https://github.com/arun0009/idempotent"}""";

        assetJson = String.format(assetJson, "Asset API-" + i);
        i = i + 1;
        ResultActions resultActions = mockMvc.perform(post("/redis/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assetJson))
                .andExpect(status().isOk());
        MvcResult mvcResult = resultActions.andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString();
        Assertions.assertEquals(expectedResponseJson, responseBody);
    }
}
