package io.github.arun0009.idempotent.redis;

import io.github.arun0009.idempotent.core.IdempotentTest;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisIdempotentController {

    @Idempotent(key = "#asset.id", duration = "PT1M")
    @PostMapping("/redis/assets")
    public IdempotentTest.AssetResponse createAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/create/idempotent");
    }

    @Idempotent(key = "#asset.type", duration = "PT1M")
    @PutMapping("/redis/assets")
    public IdempotentTest.AssetResponse updateAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }
}
