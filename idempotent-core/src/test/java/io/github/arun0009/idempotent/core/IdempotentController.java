package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.aspect.IdempotentAspect;
import io.github.arun0009.idempotent.core.persistence.InMemoryIdempotentStore;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdempotentController {

    @Bean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect(new InMemoryIdempotentStore());
    }

    @Idempotent(key = "#asset.id", ttlInSeconds = 60)
    @PostMapping("/in-memory/assets")
    public IdempotentTest.AssetResponse createAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/create/idempotent");
    }

    @Idempotent(key = "#asset.type", ttlInSeconds = 60)
    @PutMapping("/in-memory/assets")
    public IdempotentTest.AssetResponse updateAsset(@RequestBody IdempotentTest.Asset asset) {
        return new IdempotentTest.AssetResponse(
                asset.id(), asset.type(), asset.name(), "https://github.com/arun0009/update/idempotent");
    }
}
