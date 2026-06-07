package io.github.arun0009.idempotent.dynamo;

import io.github.arun0009.idempotent.core.IdempotentTest;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DynamoIdempotentController {

    @Idempotent(key = "#asset.id", duration = "PT1M")
    @PostMapping("/dynamo/assets")
    public IdempotentTest.AssetResponse createAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/create/idempotent");
    }

    @Idempotent(key = "#asset.type", duration = "PT1M")
    @PutMapping("/dynamo/assets")
    public IdempotentTest.AssetResponse updateAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }

    @Idempotent(key = "#id", duration = "PT1M")
    @PatchMapping("/dynamo/assets/{id}")
    public ResponseEntity<IdempotentTest.AssetResponse> patchAsset(
            @PathVariable String id, @RequestBody IdempotentTest.Asset asset) {
        var body = new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/patch/idempotent");
        return ResponseEntity.status(201).header("X-Trace", id).body(body);
    }
}
