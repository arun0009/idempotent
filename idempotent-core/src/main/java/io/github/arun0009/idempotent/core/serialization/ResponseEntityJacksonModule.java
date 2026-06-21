package io.github.arun0009.idempotent.core.serialization;

import org.springframework.http.ResponseEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson module that round-trips {@link ResponseEntity} via the wire form {@code {status, headers,
 * body}}. The body is written through the configured {@link SerializationContext} so default
 * typing (if enabled) preserves its concrete type. The wire {@code @class} stays
 * {@code ResponseEntity}, so callers can deserialize as either {@code ResponseEntity.class} or
 * {@code Object.class}.
 */
public final class ResponseEntityJacksonModule extends SimpleModule {

    public ResponseEntityJacksonModule() {
        super("ResponseEntityJacksonModule", Version.unknownVersion());
        addSerializer(new ResponseEntitySerializer());
        addDeserializer(ResponseEntity.class, new ResponseEntityDeserializer());
    }

    private static final class ResponseEntitySerializer extends ValueSerializer<ResponseEntity<?>> {

        @Override
        public Class<?> handledType() {
            return ResponseEntity.class;
        }

        @Override
        public void serialize(ResponseEntity<?> value, JsonGenerator gen, SerializationContext ctx)
                throws JacksonException {
            gen.writeStartObject();
            writeFields(value, gen, ctx);
            gen.writeEndObject();
        }

        @Override
        public void serializeWithType(
                ResponseEntity<?> value, JsonGenerator gen, SerializationContext ctx, TypeSerializer typeSer)
                throws JacksonException {
            var typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
            typeSer.writeTypePrefix(gen, ctx, typeId);
            writeFields(value, gen, ctx);
            typeSer.writeTypeSuffix(gen, ctx, typeId);
        }

        private void writeFields(ResponseEntity<?> value, JsonGenerator gen, SerializationContext ctx)
                throws JacksonException {
            var payload = ResponseEntityAdapter.toPayload(value);
            gen.writeNumberProperty("status", payload.status());
            gen.writeName("headers");
            ctx.writeValue(gen, payload.headers());
            gen.writeName("body");
            ctx.writeValue(gen, payload.body());
        }
    }

    private static final class ResponseEntityDeserializer extends ValueDeserializer<ResponseEntity<?>> {

        @Override
        public Class<?> handledType() {
            return ResponseEntity.class;
        }

        @Override
        public ResponseEntity<?> deserialize(JsonParser p, DeserializationContext ctx) throws JacksonException {
            int status = 200;
            Map<String, List<String>> headers = new LinkedHashMap<>();
            Object body = null;
            var token = p.currentToken();
            if (token == JsonToken.START_OBJECT) {
                token = p.nextToken();
            }
            while (token != JsonToken.END_OBJECT && token != null) {
                var name = p.currentName();
                p.nextToken();
                switch (name) {
                    case "status" -> status = p.getIntValue();
                    case "headers" -> headers = ctx.readValue(p, new TypeReference<>() {});
                    case "body" -> body = ctx.readValue(p, Object.class);
                    default -> p.skipChildren();
                }
                token = p.nextToken();
            }
            return ResponseEntityAdapter.fromPayload(new ResponseEntityPayload(status, headers, body));
        }
    }
}
