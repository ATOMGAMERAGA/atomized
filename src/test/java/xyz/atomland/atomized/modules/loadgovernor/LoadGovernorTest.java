package xyz.atomland.atomized.modules.loadgovernor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadGovernorTest {

    // 60 FPS target -> 16.667 ms target frame time.
    private static LoadGovernor governor() {
        return new LoadGovernor(60, 1.2, 0.9, 0.20, 0.05, 0.6, 0.5);
    }

    @Test
    void startsAtFullQuality() {
        LoadGovernor g = governor();
        assertEquals(0.0, g.level());
        assertFalse(g.isThrottling());
        assertEquals(1.0, g.entityDistanceScale(), 1e-9);
        assertEquals(1.0, g.particleBudgetScale(), 1e-9);
    }

    @Test
    void constructorValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(0, 1.2, 0.9, 0.2, 0.05, 0.6, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 0.9, 0.9, 0.2, 0.05, 0.6, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 1.2, 1.1, 0.2, 0.05, 0.6, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 1.2, 0.9, 0.0, 0.05, 0.6, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 1.2, 0.9, 0.2, 2.0, 0.6, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 1.2, 0.9, 0.2, 0.05, 0.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LoadGovernor(60, 1.2, 0.9, 0.2, 0.05, 0.6, 1.5));
    }

    @Test
    void overloadRampsLevelUp() {
        LoadGovernor g = governor();
        // 50 ms frame (~20 FPS), well above 16.667 * 1.2 = 20 ms.
        assertEquals(0.20, g.update(50.0), 1e-9);
        assertEquals(0.40, g.update(50.0), 1e-9);
        assertTrue(g.isThrottling());
    }

    @Test
    void levelClampsAtOne() {
        LoadGovernor g = governor();
        for (int i = 0; i < 20; i++) {
            g.update(80.0);
        }
        assertEquals(1.0, g.level(), 1e-9);
        assertEquals(0.6, g.entityDistanceScale(), 1e-9);
        assertEquals(0.5, g.particleBudgetScale(), 1e-9);
    }

    @Test
    void recoveryRampsDownGraduallyAndSlowerThanUp() {
        LoadGovernor g = governor();
        g.update(50.0); // up to 0.20 in one step
        // Good frames (10 ms < 16.667 * 0.9 = 15 ms) recover only 0.05 per step.
        assertEquals(0.15, g.update(10.0), 1e-9);
        assertEquals(0.10, g.update(10.0), 1e-9);
        assertEquals(0.05, g.update(10.0), 1e-9);
        assertEquals(0.0, g.update(10.0), 1e-9);
    }

    @Test
    void levelClampsAtZero() {
        LoadGovernor g = governor();
        // Already at full quality; more good frames keep it at 0.
        assertEquals(0.0, g.update(5.0), 1e-9);
        assertEquals(0.0, g.level());
    }

    @Test
    void hysteresisBandHoldsLevelSteady() {
        LoadGovernor g = governor();
        g.update(50.0); // level 0.20
        double held = g.level();
        // Frame time inside [15 ms, 20 ms] -> neither ramp; hold.
        assertEquals(held, g.update(17.0), 1e-9);
        assertEquals(held, g.update(18.5), 1e-9);
        assertEquals(held, g.update(15.5), 1e-9);
        assertEquals(0.20, g.level(), 1e-9);
    }

    @Test
    void boundaryFramesDoNotTrigger() {
        LoadGovernor g = governor();
        // Exactly at the thresholds: strictly-greater / strictly-less, so no change.
        assertEquals(0.0, g.update(20.0), 1e-9); // == high, not > high
        assertEquals(0.0, g.update(15.0), 1e-9); // == low, not < low
    }

    @Test
    void scalesInterpolateWithLevel() {
        LoadGovernor g = governor();
        g.update(50.0); // 0.20
        // entity scale = lerp(1.0, 0.6, 0.2) = 0.92 ; particle = lerp(1.0, 0.5, 0.2) = 0.90
        assertEquals(0.92, g.entityDistanceScale(), 1e-9);
        assertEquals(0.90, g.particleBudgetScale(), 1e-9);
    }

    @Test
    void resetRestoresFullQuality() {
        LoadGovernor g = governor();
        g.update(80.0);
        g.update(80.0);
        g.reset();
        assertEquals(0.0, g.level());
        assertFalse(g.isThrottling());
        assertEquals(1.0, g.entityDistanceScale(), 1e-9);
    }

    @Test
    void spikeThenRecoverFullCycle() {
        LoadGovernor g = governor();
        // Sustained overload pins to 1.0...
        for (int i = 0; i < 10; i++) {
            g.update(60.0);
        }
        assertEquals(1.0, g.level(), 1e-9);
        // ...then a long calm stretch returns to full quality.
        for (int i = 0; i < 25; i++) {
            g.update(8.0);
        }
        assertEquals(0.0, g.level(), 1e-9);
    }
}
