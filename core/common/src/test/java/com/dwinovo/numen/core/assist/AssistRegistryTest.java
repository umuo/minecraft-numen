package com.dwinovo.numen.core.assist;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssistRegistryTest {

    @Test
    void defaultsAreSafeAndDisabled() {
        AssistConfig config = new AssistRegistry().get(UUID.randomUUID());

        assertFalse(config.enabled());
        assertEquals(10, config.radius());
        assertEquals(4, config.keepDistance());
    }

    @Test
    void settingsSurviveSaveAndLoad() {
        UUID companion = UUID.randomUUID();
        AssistRegistry registry = new AssistRegistry();
        registry.put(companion, new AssistConfig(true, false, true, false, 14, 6));

        AssistRegistry loaded = AssistRegistry.load(
                registry.save(new CompoundTag(), null), null);

        assertEquals(new AssistConfig(true, false, true, false, 14, 6), loaded.get(companion));
    }

    @Test
    void numericSettingsAreClampedAtThePersistenceBoundary() {
        AssistConfig config = new AssistConfig(true, true, true, true, 99, -4);

        assertEquals(AssistConfig.MAX_RADIUS, config.radius());
        assertEquals(AssistConfig.MIN_KEEP_DISTANCE, config.keepDistance());
    }
}
