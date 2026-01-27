package io.github.arun0009.idempotent.redis;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConfigurationProcessorTest {

    @Test
    void configurationMetadataIsPresentOnClasspath() {
        URL url = getClass().getClassLoader().getResource("META-INF/spring-configuration-metadata.json");
        assertNotNull(url);

        byte[] content = assertDoesNotThrow(() -> Files.readAllBytes(Path.of(url.toURI())));
        Map<String, Object> root = JsonMapper.shared().readValue(content, new TypeReference<>() {});

        assertNotNull(root);
        assertTrue(root.containsKey("groups"));
        assertTrue(root.containsKey("properties"));
        assertTrue(root.containsKey("hints"));
        assertTrue(root.containsKey("ignored"));

        @SuppressWarnings("unchecked")
        Set<String> groupNames = ((List<Map<String, String>>) root.get("groups"))
                .stream().map(p -> p.get("name")).collect(toSet());
        assertEquals(Set.of("idempotent.redis"), groupNames);

        @SuppressWarnings("unchecked")
        Set<String> propertyNames = ((List<Map<String, String>>) root.get("properties"))
                .stream().map(p -> p.get("name")).collect(toSet());

        assertEquals(
                Set.of(
                        "idempotent.redis.standalone.host",
                        "idempotent.redis.auth.enabled",
                        "idempotent.redis.ssl.enabled",
                        "idempotent.redis.auth.username",
                        "idempotent.redis.auth.password",
                        "idempotent.redis.cluster.enabled",
                        "idempotent.redis.cluster.hosts",
                        "idempotent.redis.sentinel.enabled",
                        "idempotent.redis.sentinel.master",
                        "idempotent.redis.sentinel.nodes"),
                propertyNames);
    }
}
