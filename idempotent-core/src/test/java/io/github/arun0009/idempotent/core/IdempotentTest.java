package io.github.arun0009.idempotent.core;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IdempotentTest {

    public record AssetType(String category, String version) {}

    public record Asset(String id, AssetType type, String name) {}

    public record AssetResponse(String id, AssetType type, String name, String url) {}

    int i = 1;

    public void validateAssetResponse(MockMvc mockMvc, String type, MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        String assetJsonTemplate =
                """
                 {
                     "id": 1,
                     "type": {
                         "category": "%s API",
                         "version" : "1"
                      },
                     "name": "%s"
                 }
                """;
        String assetJson = String.format(assetJsonTemplate, type, "Asset API-" + i);
        i = i + 1;
        ResultActions resultActions = mockMvc.perform(
                        requestBuilder.contentType(MediaType.APPLICATION_JSON).content(assetJson))
                .andExpect(status().isOk());
        MvcResult mvcResult = resultActions.andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString();
        String expectedResponseJson =
                """
                {"id":"1","type":{"category":"%s API","version":"1"},"name":"Asset API-1","url":"https://github.com/arun0009/%s/idempotent"}""";
        Assertions.assertEquals(String.format(expectedResponseJson, type, type.toLowerCase()), responseBody);
    }
}
