package com.dwinovo.numen.core.act;

/**
 * Ownership latch for a temporary Shift input. It remembers the state that existed before
 * the action so cleanup does not cancel a sneak held by another movement/action owner.
 */
public final class SneakLatch {

    private boolean held;
    private boolean restore;

    /** Claim Shift and return the state that should be written to the player. */
    public boolean hold(boolean current) {
        if (!held) {
            restore = current;
            held = true;
        }
        return true;
    }

    /** Return the pre-action state, or the current state if this latch owns nothing. */
    public boolean release(boolean current) {
        if (!held) return current;
        held = false;
        return restore;
    }

    public boolean held() {
        return held;
    }
}
