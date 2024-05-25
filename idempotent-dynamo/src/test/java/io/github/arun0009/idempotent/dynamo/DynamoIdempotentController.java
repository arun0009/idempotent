package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.IdempotentTest;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DynamoIdempotentController {

    @Idempotent(key = "#asset.id", ttlInSeconds = 60)
    @PostMapping("/dynamo/assets")
    public IdempotentTest.AssetResponse createAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/create/idempotent");
    }

    @Idempotent(key = "#asset.type", ttlInSeconds = 60)
    @PutMapping("/dynamo/assets")
    public IdempotentTest.AssetResponse updateAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }
}
