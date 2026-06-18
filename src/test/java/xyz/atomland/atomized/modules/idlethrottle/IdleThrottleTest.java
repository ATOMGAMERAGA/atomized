package xyz.atomland.atomized.modules.idlethrottle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleThrottleTest {

    private static final long GRACE = 500_000_000L; // 0.5 s

    @Test
    void activeWindowIsNeverCapped() {
        IdleThrottle t = new IdleThrottle(30, GRACE);
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(true, 0L));
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(true, 10_000_000_000L));
    }

    @Test
    void inactiveWithinGraceIsNotYetCapped() {
        IdleThrottle t = new IdleThrottle(30, GRACE);
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(false, 0L));          // focus just lost
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(false, GRACE - 1));   // still in grace
    }

    @Test
    void inactiveBeyondGraceIsCapped() {
        IdleThrottle t = new IdleThrottle(30, GRACE);
        t.targetFps(false, 0L);                       // start the clock
        assertEquals(30, t.targetFps(false, GRACE));  // grace elapsed
        assertEquals(30, t.targetFps(false, GRACE * 4));
    }

    @Test
    void regainingFocusResetsGrace() {
        IdleThrottle t = new IdleThrottle(30, GRACE);
        t.targetFps(false, 0L);
        assertEquals(30, t.targetFps(false, GRACE));   // throttling
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(true, GRACE + 1)); // focus back
        // Losing focus again must restart the grace period, not throttle immediately.
        assertEquals(IdleThrottle.UNCAPPED, t.targetFps(false, GRACE + 2));
        assertEquals(30, t.targetFps(false, GRACE + 2 + GRACE));
    }

    @Test
    void zeroGraceThrottlesImmediately() {
        IdleThrottle t = new IdleThrottle(15, 0L);
        assertEquals(15, t.targetFps(false, 0L));
    }

    @Test
    void isThrottlingMatchesTarget() {
        IdleThrottle t = new IdleThrottle(30, GRACE);
        assertFalse(t.isThrottling(true, 0L));
        t.targetFps(false, 0L);
        assertFalse(t.isThrottling(false, GRACE - 1));
        assertTrue(t.isThrottling(false, GRACE));
    }

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new IdleThrottle(0, GRACE));
        assertThrows(IllegalArgumentException.class, () -> new IdleThrottle(-5, GRACE));
        assertThrows(IllegalArgumentException.class, () -> new IdleThrottle(30, -1));
    }
}
