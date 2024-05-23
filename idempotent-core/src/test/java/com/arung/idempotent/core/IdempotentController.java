package com.codeweave.idempotent.core;

import com.codeweave.idempotent.core.annotation.Idempotent;
import com.codeweave.idempotent.core.aspect.IdempotentAspect;
import com.codeweave.idempotent.core.persistence.InMemoryIdempotentStore;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdempotentController {

    public record Asset(String id, String type, String name) {}

    public record AssetResponse(String id, String type, String name, String url) {}

    @Bean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect(new InMemoryIdempotentStore());
    }

    @Idempotent(key = "#asset.id", ttlInSeconds = 60)
    @PostMapping("/assets")
    public AssetResponse createAsset(@RequestBody Asset asset) {
        System.out.println(asset.name);
        return new AssetResponse(asset.id, asset.type, asset.name, "https://github.com/arun0009/idempotent");
    }
}
