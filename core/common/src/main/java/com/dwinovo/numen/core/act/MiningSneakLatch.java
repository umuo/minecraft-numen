package com.dwinovo.numen.core.act;

/**
 * State latch for a temporary mining-owned sneak input. It remembers the state that existed
 * before mining so cleanup does not cancel a sneak held by another movement/action owner.
 */
final class MiningSneakLatch {

    private boolean held;
    private boolean restore;

    /** Return the sneak state the player should have after applying the current mode. */
    boolean update(boolean enabled, boolean current) {
        if (!enabled) return release(current);
        if (!held) {
            restore = current;
            held = true;
        }
        return true;
    }

    /** Return the pre-mining state, or the current state if this latch owns nothing. */
    boolean release(boolean current) {
        if (!held) return current;
        held = false;
        return restore;
    }

    boolean held() {
        return held;
    }
}
