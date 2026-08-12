package com.dwinovo.numen.platform.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IBlockCapabilityReaderTest {

    @Test
    void storageKindsMatchSemanticFilters() {
        var itemTank = new IBlockCapabilityReader.StorageKinds(true, true, false);

        assertTrue(itemTank.matches("items"));
        assertTrue(itemTank.matches("fluids"));
        assertTrue(itemTank.matches("all"));
        assertFalse(itemTank.matches("energy"));
        assertFalse(itemTank.matches("unknown"));
    }

    @Test
    void emptyKindsDoNotMatchAll() {
        assertFalse(new IBlockCapabilityReader.StorageKinds(false, false, false).matches("all"));
    }
}
