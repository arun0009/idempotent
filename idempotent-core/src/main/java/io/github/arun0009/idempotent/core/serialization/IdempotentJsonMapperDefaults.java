package io.github.arun0009.idempotent.core.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

/**
 * Default Jackson settings for idempotent value serialization (polymorphic return types).
 */
public final class IdempotentJsonMapperDefaults {

    private IdempotentJsonMapperDefaults() {}

    /**
     * Applies permissive default typing so arbitrary response types round-trip. Suitable only when Redis / DB
     * contents are trusted.
     */
    public static void applyPermissivePolymorphicTyping(JsonMapper.Builder builder, Logger log) {
        log.warn("Using an unrestricted polymorphic type validator for idempotent payload serialization. "
                + "Without a restricted PolymorphicTypeValidator, deserialization is vulnerable to "
                + "arbitrary code execution when reading from untrusted sources.");
        var ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType((ctx, clazz) -> true)
                .build();
        builder.polymorphicTypeValidator(ptv)
                .setDefaultTyping(new DefaultTypeResolverBuilder(
                        ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class"));
    }
}
