package io.github.arun0009.idempotent.core.serialization;

import tools.jackson.databind.json.JsonMapper;

/** Customizes the {@link JsonMapper} used by the default JSON payload codec. */
@FunctionalInterface
public interface IdempotentJsonMapperCustomizer {

    /**
     * @param builder the mapper builder; mutate in place
     */
    void customize(JsonMapper.Builder builder);
}
