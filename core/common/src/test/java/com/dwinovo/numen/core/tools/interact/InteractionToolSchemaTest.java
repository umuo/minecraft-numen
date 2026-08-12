package com.dwinovo.numen.core.tools.interact;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionToolSchemaTest {

    @Test
    void blockAndEntityClicksExposeTheSneakModifier() {
        assertRequiredSneak(new InteractAtTool().parameterSchema());
        assertRequiredSneak(new InteractEntityTool().parameterSchema());
    }

    @SuppressWarnings("unchecked")
    private static void assertRequiredSneak(Map<String, Object> schema) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(properties.containsKey("sneak"));
        assertTrue(required.contains("sneak"));
    }
}
