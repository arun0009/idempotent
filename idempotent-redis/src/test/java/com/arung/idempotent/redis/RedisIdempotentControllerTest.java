package com.arung.idempotent.redis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootApplication(scanBasePackages = "com.arung.idempotent.redis")
@SpringBootTest
@ContextConfiguration(initializers = com.arung.idempotent.redis.RedisIdempotentControllerTest.Initializer.class)
@TestPropertySource(properties = {
        "spring.data.redis.auth.enabled=false",
        "spring.data.redis.ssl.enabled=false",
        "spring.data.redis.auth.password=nopassword",
        "spring.data.redis.cluster.enabled=false"
})
public class RedisIdempotentControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    int i = 1;

    private MockMvc mockMvc;

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static GenericContainer redis = new GenericContainer<>("redis:6.2.6")
                .withExposedPorts(6379)
                .withReuse(true);

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // Start container
            redis.start();

            // Override Redis configuration
            String redisContainerIP = "spring.data.redis.host=" + redis.getHost();
            String redisContainerPort = "spring.data.redis.port=" + redis.getMappedPort(6379);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,  redisContainerIP, redisContainerPort);
        }
    }

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    void createAsset() throws Exception {
        String assetJson = """
                {
                    "id": 1,
                    "type": "API",
                    "name": "%s"
                }
                """;

        String expectedResponseJson = """
                {"id":"1","type":"API","name":"Asset API-1","url":"https://github.com/arun0009/idempotent"}""";

        assetJson = String.format(assetJson, "Asset API-" + i);
        i = i + 1;
        ResultActions resultActions = mockMvc.perform(post("/assets").contentType(MediaType.APPLICATION_JSON).content(assetJson)).andExpect(status().isOk());
        MvcResult mvcResult = resultActions.andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString();
        Assertions.assertEquals(expectedResponseJson, responseBody);
    }
}
