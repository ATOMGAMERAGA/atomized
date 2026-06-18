package xyz.atomland.atomized.modules.particlecontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBudgetTest {

    @Test
    void admitsUpToBudgetThenDrops() {
        ParticleBudget b = new ParticleBudget(3);
        b.beginTick();
        assertTrue(b.tryAdd());
        assertTrue(b.tryAdd());
        assertTrue(b.tryAdd());
        assertFalse(b.tryAdd()); // 4th over a budget of 3
        assertFalse(b.tryAdd());
        assertEquals(3, b.addedThisTick());
    }

    @Test
    void beginTickRefillsAllowance() {
        ParticleBudget b = new ParticleBudget(2);
        b.beginTick();
        assertTrue(b.tryAdd());
        assertTrue(b.tryAdd());
        assertFalse(b.tryAdd());
        b.beginTick(); // new tick
        assertTrue(b.tryAdd());
        assertTrue(b.tryAdd());
        assertFalse(b.tryAdd());
    }

    @Test
    void droppedTotalAccumulatesAcrossTicks() {
        ParticleBudget b = new ParticleBudget(1);
        b.beginTick();
        b.tryAdd();
        b.tryAdd(); // drop 1
        b.tryAdd(); // drop 2
        b.beginTick();
        b.tryAdd();
        b.tryAdd(); // drop 3
        assertEquals(3, b.droppedTotal());
    }

    @Test
    void zeroBudgetDropsEverything() {
        ParticleBudget b = new ParticleBudget(0);
        b.beginTick();
        assertFalse(b.tryAdd());
        assertEquals(0, b.addedThisTick());
        assertEquals(1, b.droppedTotal());
    }

    @Test
    void generousBudgetNeverDropsNormalLoad() {
        ParticleBudget b = new ParticleBudget(2048);
        b.beginTick();
        for (int i = 0; i < 500; i++) {
            assertTrue(b.tryAdd());
        }
        assertEquals(0, b.droppedTotal());
    }

    @Test
    void budgetIsReconfigurable() {
        ParticleBudget b = new ParticleBudget(1);
        b.setMaxNewPerTick(5);
        b.beginTick();
        for (int i = 0; i < 5; i++) {
            assertTrue(b.tryAdd());
        }
        assertFalse(b.tryAdd());
    }

    @Test
    void negativeBudgetRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ParticleBudget(-1));
        ParticleBudget b = new ParticleBudget(4);
        assertThrows(IllegalArgumentException.class, () -> b.setMaxNewPerTick(-3));
    }

    @Test
    void tryAddBeforeBeginTickUsesFullBudgetOnce() {
        // Construction leaves addedThisTick=0, so it behaves as a fresh tick until beginTick.
        ParticleBudget b = new ParticleBudget(2);
        assertTrue(b.tryAdd());
        assertTrue(b.tryAdd());
        assertFalse(b.tryAdd());
    }
}
