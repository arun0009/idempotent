package io.github.arun0009.idempotent.nats;

import tools.jackson.databind.json.JsonMapper;

@FunctionalInterface
public interface IdempotentJacksonJsonBuilderCustomizer {

    /**
     * Customizes the provided {@link JsonMapper.Builder} instance. This method allows for
     * modifications or enhancements to the builder configuration, such as configuring additional
     * modules, serialization settings, or type resolvers.
     *
     * @param builder the {@link JsonMapper.Builder} instance to customize
     */
    void customize(JsonMapper.Builder builder);
}
