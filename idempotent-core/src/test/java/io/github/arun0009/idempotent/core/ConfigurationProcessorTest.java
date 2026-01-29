package io.github.arun0009.idempotent.core;

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

class ConfigurationProcessorTest {

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
        Set<String> names = ((List<Map<String, String>>) root.get("properties"))
                .stream().map(p -> p.get("name")).collect(toSet());

        assertEquals(
                Set.of(
                        "idempotent.key.header",
                        "idempotent.inprogress.max.retries",
                        "idempotent.inprogress.retry.initial.intervalMillis",
                        "idempotent.inprogress.retry.multiplier"),
                names);
    }
}
