package com.dwinovo.numen.client.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkBlockMemoryTest {

    @Test
    void vanillaStationsAcceptLegacyPathsAndNamespacedIds() {
        assertTrue(WorkBlockMemory.isTracked("chest"));
        assertTrue(WorkBlockMemory.isTracked("minecraft:chest"));
        assertTrue(WorkBlockMemory.isTracked("minecraft:crafting_table"));
    }

    @Test
    void unknownIdsNeedAStorageCapabilityBeforeTheyAreRemembered() {
        assertFalse(WorkBlockMemory.isTracked("examplemod:ornamental_crate"));
        assertFalse(WorkBlockMemory.isTracked(null));
    }
}
