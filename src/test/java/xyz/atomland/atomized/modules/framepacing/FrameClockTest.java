package xyz.atomland.atomized.modules.framepacing;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameClockTest {

    @ParameterizedTest
    @CsvSource({
            "60, true, false",
            "120, true, false",
            "0, false, false",
            "-1, false, false",
            "60, false, true"  // vsync on -> never pace
    })
    void shouldPaceOnlyWhenLimitedAndNoVsync(int fps, boolean expected, boolean vsync) {
        assertEquals(expected, FrameClock.shouldPace(fps, vsync));
    }

    @ParameterizedTest
    @CsvSource({
            "60, 16666667",
            "120, 8333333",
            "30, 33333333",
            "144, 6944444"
    })
    void intervalNanosForCommonRates(int fps, long expectedNanos) {
        assertEquals(expectedNanos, FrameClock.intervalNanosFor(fps));
    }

    @Test
    void intervalIsZeroForUnlimited() {
        assertEquals(0L, FrameClock.intervalNanosFor(0));
        assertEquals(0L, FrameClock.intervalNanosFor(-5));
    }

    @Test
    void nextDeadlineAdvancesByOneInterval() {
        long interval = 10_000_000L;
        assertEquals(1_010_000_000L, FrameClock.computeNextDeadline(1_000_000_000L, 1_000_000_000L, interval));
    }

    @Test
    void nextDeadlineKeepsScheduleWhenSlightlyLate() {
        long interval = 10_000_000L;
        // We're a little past the previous deadline but less than one interval behind.
        long previous = 1_000_000_000L;
        long now = previous + 5_000_000L;
        assertEquals(previous + interval, FrameClock.computeNextDeadline(previous, now, interval));
    }

    @Test
    void nextDeadlineRebasesAfterLongStall() {
        long interval = 10_000_000L;
        long previous = 1_000_000_000L;
        long now = previous + 100_000_000L; // 10 intervals behind: a real stall
        // Backlog dropped: rebased to now + interval, not previous + interval.
        assertEquals(now + interval, FrameClock.computeNextDeadline(previous, now, interval));
    }

    @Test
    void parkBudgetLeavesSpinTail() {
        assertEquals(8_000_000L, FrameClock.parkBudget(10_000_000L, 2_000_000L));
    }

    @Test
    void parkBudgetZeroWithinSpinTail() {
        assertEquals(0L, FrameClock.parkBudget(2_000_000L, 2_000_000L));
        assertEquals(0L, FrameClock.parkBudget(1_000_000L, 2_000_000L));
    }

    @Test
    void parkBudgetZeroWhenPastDeadline() {
        assertEquals(0L, FrameClock.parkBudget(-5_000_000L, 2_000_000L));
    }

    @Test
    void constructorRejectsNegativeSpinTail() {
        assertThrows(IllegalArgumentException.class,
                () -> new FrameClock(System::nanoTime, n -> {
                }, -1));
    }

    @Test
    void firstPaceFrameOnlyPrimesSchedule() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        AtomicLong parkedTotal = new AtomicLong();
        FrameClock fc = new FrameClock(clock::get, parkedTotal::addAndGet, 0L);

        fc.pace(60, false);
        // First call must not wait — it just establishes the deadline.
        assertEquals(0L, parkedTotal.get());
        assertEquals(1_000_000_000L + FrameClock.intervalNanosFor(60), fc.currentDeadline());
    }

    @Test
    void pacingParksUntilDeadline() {
        // Clock that advances when the parker is called, simulating real parking.
        AtomicLong clock = new AtomicLong(0L);
        FrameClock.Parker parker = nanos -> clock.addAndGet(nanos);
        // spin tail 0 so the whole wait is parked and deterministic.
        FrameClock fc = new FrameClock(clock::get, parker, 0L);

        long interval = FrameClock.intervalNanosFor(60);
        fc.pace(60, false);          // primes deadline at 0 + interval
        // Next frame: render took ~no time, clock still ~interval-ahead target.
        fc.pace(60, false);          // should park until 2*interval
        assertEquals(2 * interval, fc.currentDeadline());
        assertTrue(clock.get() >= 2 * interval);
    }

    @Test
    void disabledPacingDoesNotPark() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicLong parkedTotal = new AtomicLong();
        FrameClock fc = new FrameClock(clock::get, parkedTotal::addAndGet, 0L);

        fc.pace(0, false);   // unlimited
        fc.pace(60, true);   // vsync
        assertEquals(0L, parkedTotal.get());
    }

    @Test
    void resetRePrimesSchedule() {
        AtomicLong clock = new AtomicLong(0L);
        FrameClock.Parker parker = nanos -> clock.addAndGet(nanos);
        FrameClock fc = new FrameClock(clock::get, parker, 0L);

        fc.pace(60, false); // prime
        fc.reset();
        long before = clock.get();
        fc.pace(60, false); // primes again instead of waiting
        assertEquals(before, clock.get());
    }

    @Test
    void switchingToUnlimitedUnprimes() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicLong parkedTotal = new AtomicLong();
        FrameClock.Parker parker = nanos -> {
            parkedTotal.addAndGet(nanos);
            clock.addAndGet(nanos);
        };
        FrameClock fc = new FrameClock(clock::get, parker, 0L);

        fc.pace(60, false); // prime
        fc.pace(0, false);  // unlimited -> unprime, no park
        long parkedAfterUnlimited = parkedTotal.get();
        assertEquals(0L, parkedAfterUnlimited);
        // Re-enabling must prime again (next call waits, the one after does not over-wait).
        fc.pace(60, false); // prime
        assertFalse(fc.currentDeadline() == 0L);
    }
}
