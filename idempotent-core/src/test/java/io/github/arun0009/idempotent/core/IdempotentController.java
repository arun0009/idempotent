package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class IdempotentController {

    @Idempotent(key = "#asset.id", duration = "PT1M")
    @PostMapping("/in-memory/assets")
    public IdempotentTest.AssetResponse createAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/create/idempotent");
    }

    @Idempotent(key = "#asset.type", duration = "PT1M")
    @PutMapping("/in-memory/assets")
    public IdempotentTest.AssetResponse updateAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }

    @Idempotent(key = "#asset.id", duration = "PT1M")
    @PutMapping("/in-memory/assets-error")
    public IdempotentTest.AssetResponse updateAssetError(@RequestBody IdempotentTest.Asset asset) {
        throw new NotFoundException("Ops... Asset not found!", asset.id());
    }

    @Idempotent(key = "#asset.id", duration = "PT1M")
    @PutMapping("/in-memory/assets-error-heavy")
    public IdempotentTest.AssetResponse updateAssetErrorHeavy(@RequestBody IdempotentTest.Asset asset)
            throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }
}
