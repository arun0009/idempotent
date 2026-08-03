package io.github.arun0009.idempotent.core.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

/**
 * Default Jackson settings for idempotent value serialization (polymorphic return types).
 */
public final class IdempotentJsonMapperDefaults {

    private IdempotentJsonMapperDefaults() {}

    /**
     * Builds a {@link JsonMapper} preconfigured for idempotent payload serialization: permissive
     * polymorphic typing plus the {@link ResponseEntityJacksonModule} when Spring is on the
     * classpath. Suitable only when store contents are trusted.
     */
    public static JsonMapper buildPermissiveMapper() {
        var builder = JsonMapper.builder();
        applyPermissivePolymorphicTyping(builder);
        addResponseEntityModuleIfPresent(builder);
        return builder.build();
    }

    /**
     * Registers the {@link ResponseEntityJacksonModule} so {@code ResponseEntity} payloads
     * round-trip, when Spring is on the classpath.
     */
    public static void addResponseEntityModuleIfPresent(JsonMapper.Builder builder) {
        if (Utils.isResponseEntityPresent()) {
            builder.addModules(new ResponseEntityJacksonModule());
        }
    }

    /**
     * Applies permissive default typing so arbitrary response types round-trip. Covers Java records,
     * Kotlin data classes, and all other final types. Suitable only when store contents are trusted.
     */
    public static void applyPermissivePolymorphicTyping(JsonMapper.Builder builder) {
        var ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType((ctx, clazz) -> true)
                .build();
        builder.polymorphicTypeValidator(ptv).setDefaultTyping(new AllTypesResolverBuilder(ptv));
    }

    /**
     * Type resolver that writes {@code @class} for all types, including final classes like
     * Java records and Kotlin data classes.
     */
    private static final class AllTypesResolverBuilder extends DefaultTypeResolverBuilder {

        AllTypesResolverBuilder(PolymorphicTypeValidator ptv) {
            super(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY, JsonTypeInfo.Id.CLASS, "@class");
        }

        @Override
        public boolean useForType(JavaType t) {
            return true;
        }
    }
}
