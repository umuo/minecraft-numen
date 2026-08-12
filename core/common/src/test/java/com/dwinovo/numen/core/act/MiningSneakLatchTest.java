package com.dwinovo.numen.core.act;

import com.dwinovo.numen.core.pathing.settings.NavSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSneakLatchTest {

    @Test
    void modeIsEnabledByDefault() {
        assertTrue(NavSettings.get().miningInputMode
                == NavSettings.MiningInputMode.SNEAK_WHILE_MINING);
    }

    @Test
    void holdsSneakUntilMiningReleasesIt() {
        MiningSneakLatch latch = new MiningSneakLatch();

        assertTrue(latch.update(true, false));
        assertTrue(latch.update(true, false));
        assertFalse(latch.release(true));
        assertFalse(latch.held());
    }

    @Test
    void restoresSneakThatWasAlreadyHeldBeforeMining() {
        MiningSneakLatch latch = new MiningSneakLatch();

        assertTrue(latch.update(true, true));
        assertTrue(latch.release(false));
    }

    @Test
    void disablingModeMidBreakReleasesTheOwnedInput() {
        MiningSneakLatch latch = new MiningSneakLatch();

        assertTrue(latch.update(true, false));
        assertFalse(latch.update(false, true));
        assertFalse(latch.held());
    }

    @Test
    void disabledModeDoesNotClaimOrChangeSneak() {
        MiningSneakLatch latch = new MiningSneakLatch();

        assertFalse(latch.update(false, false));
        assertTrue(latch.update(false, true));
        assertFalse(latch.held());
    }
}
