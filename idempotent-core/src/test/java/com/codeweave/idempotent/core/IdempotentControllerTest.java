package com.codeweave.idempotent.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootApplication(scanBasePackages = "com.codeweave.idempotent.core")
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
        ResultActions resultActions = mockMvc.perform(
                        post("/assets").contentType(MediaType.APPLICATION_JSON).content(assetJson))
                .andExpect(status().isOk());
        MvcResult mvcResult = resultActions.andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString();
        Assertions.assertEquals(expectedResponseJson, responseBody);
    }
}
