package io.github.arun0009.idempotent.core.serialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tools.jackson.databind.json.JsonMapper;

/** Registers default idempotent payload serialization beans. */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(JsonMapper.class)
@EnableConfigurationProperties(IdempotentSerializationProperties.class)
public class IdempotentJsonMapperAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdempotentJsonMapperAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(IdempotentPayloadCodec.class)
    IdempotentPayloadCodec idempotentPayloadCodec(
            IdempotentSerializationProperties properties,
            ObjectProvider<IdempotentJsonMapperCustomizer> idempotentJsonMapperCustomizers) {
        return switch (properties.strategy()) {
            case JAVA -> new JdkIdempotentPayloadCodec();
            case JSON -> {
                JsonMapper.Builder builder = JsonMapper.builder();
                var customizers =
                        idempotentJsonMapperCustomizers.orderedStream().toList();
                if (customizers.isEmpty()) {
                    log.warn("Using an unrestricted polymorphic type validator for idempotent payload serialization. "
                            + "Without a restricted PolymorphicTypeValidator, deserialization is vulnerable to "
                            + "arbitrary code execution when reading from untrusted sources.");
                    IdempotentJsonMapperDefaults.applyPermissivePolymorphicTyping(builder);
                } else {
                    customizers.forEach(c -> c.customize(builder));
                }
                yield new JacksonIdempotentPayloadCodec(builder.build());
            }
        };
    }
}
