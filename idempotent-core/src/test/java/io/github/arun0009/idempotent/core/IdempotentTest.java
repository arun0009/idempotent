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

    public void validateAssetResponse(
            MockMvc mockMvc, int counter, String type, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        String assetJson = """
                 {
                     "id": 1,
                     "type": {
                         "category": "%s API",
                         "version" : "1"
                      },
                     "name": "Asset API-1"
                 }
                """.formatted(type);

        // First request - should process and cache the response
        ResultActions resultActions = mockMvc.perform(
                        requestBuilder.contentType(MediaType.APPLICATION_JSON).content(assetJson))
                .andExpect(status().isOk());

        MvcResult mvcResult = resultActions.andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString();

        // Expected response should match the first request
        String expectedResponseJson = """
                {"id":"1","type":{"category":"%s API","version":"1"},"name":"Asset API-1","url":"https://github.com/arun0009/%s/idempotent"}""".formatted(type, type.toLowerCase());

        // Verify first response is as expected
        Assertions.assertEquals(expectedResponseJson, responseBody);

        // Make the same request again - should return the same response
        resultActions = mockMvc.perform(
                        requestBuilder.contentType(MediaType.APPLICATION_JSON).content(assetJson))
                .andExpect(status().isOk());

        String cachedResponseBody = resultActions.andReturn().getResponse().getContentAsString();

        // Verify the response is the same as the first one (idempotency check)
        Assertions.assertEquals(responseBody, cachedResponseBody);
    }
}
