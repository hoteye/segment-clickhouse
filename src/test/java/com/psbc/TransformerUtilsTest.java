package com.psbc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformerUtilsTest {

    @Test
    void testLoadAndTraverseMappings() {
        // 加载 mappings
        Map<String, Object> config = ConfigLoader.loadConfig("segmentOnEvent.yaml");
        assertNotNull(config, "Config should not be null");

        Map<String, Object> mappings = (Map<String, Object>) config.get("mappings");
        assertNotNull(mappings, "Mappings should not be null");

        // 遍历 mappings
        TransformerUtils.traverseMappings(mappings);

    }

}