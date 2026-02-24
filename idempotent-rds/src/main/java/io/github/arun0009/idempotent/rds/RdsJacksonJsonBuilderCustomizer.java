package io.github.arun0009.idempotent.rds;

import tools.jackson.databind.json.JsonMapper;

/**
 * Customizer for the JsonMapper.Builder used in the RDS idempotent store.
 */
@FunctionalInterface
public interface RdsJacksonJsonBuilderCustomizer {

    /**
     * Customizes the provided {@link JsonMapper.Builder} instance.
     *
     * @param builder the {@link JsonMapper.Builder} instance to customize
     */
    void customize(JsonMapper.Builder builder);
}
