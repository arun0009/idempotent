package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DynamoIdempotentController {

    public record Asset(String id, String type, String name) {}

    public record AssetResponse(String id, String type, String name, String url) {}

    @Idempotent(key = "#asset.id", ttlInSeconds = 60)
    @PostMapping("/dynamo/assets")
    public AssetResponse createAsset(@RequestBody Asset asset) {
        return new AssetResponse(asset.id, asset.type, asset.name, "https://github.com/arun0009/idempotent");
    }
}
