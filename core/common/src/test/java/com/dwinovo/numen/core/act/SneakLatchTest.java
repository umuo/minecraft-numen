package com.dwinovo.numen.core.act;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SneakLatchTest {

    @Test
    void holdsSneakUntilTheActionReleasesIt() {
        SneakLatch latch = new SneakLatch();

        assertTrue(latch.hold(false));
        assertTrue(latch.hold(false));
        assertFalse(latch.release(true));
        assertFalse(latch.held());
    }

    @Test
    void restoresSneakThatWasAlreadyHeldBeforeTheAction() {
        SneakLatch latch = new SneakLatch();

        assertTrue(latch.hold(true));
        assertTrue(latch.release(false));
    }

    @Test
    void releaseWithoutOwnershipDoesNotChangeTheState() {
        SneakLatch latch = new SneakLatch();

        assertFalse(latch.release(false));
        assertTrue(latch.release(true));
    }
}
