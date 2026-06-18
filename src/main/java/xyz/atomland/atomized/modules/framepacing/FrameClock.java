package xyz.atomland.atomized.modules.framepacing;

import java.util.function.LongSupplier;

/**
 * Precise frame pacer (master plan §6.3). Replaces the vanilla coarse FPS limiter with a
 * hybrid park/spin wait so frame intervals are even, cutting micro-stutter and frame-time
 * jitter ("118 FPS like glass" instead of "120 FPS wobbling").
 *
 * <p>The timing decisions are pure functions of (target FPS, previous deadline, current
 * time) so they are deterministically unit-tested; the wait itself parks for most of the
 * remaining time and spins the last short tail for accuracy. The clock and the park
 * primitive are injectable for tests.
 *
 * <p>Pacing is disabled when the target FPS is unlimited (≤ 0) or when VSync is on — never
 * double-synchronize (master plan §6.3).
 */
public final class FrameClock {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /** Abstraction over {@link java.util.concurrent.locks.LockSupport#parkNanos(long)} for testability. */
    @FunctionalInterface
    public interface Parker {
        void parkNanos(long nanos);
    }

    private final LongSupplier nanoClock;
    private final Parker parker;
    private final long spinTailNanos;
    private long nextDeadline;
    private boolean primed;

    public FrameClock() {
        this(System::nanoTime, java.util.concurrent.locks.LockSupport::parkNanos, 2_000_000L);
    }

    /**
     * @param nanoClock monotonic nanosecond clock
     * @param parker parks the thread for up to the requested nanos
     * @param spinTailNanos the final stretch (nanos) spent busy-spinning instead of parking,
     *                      to absorb park oversleep; typical ~1–2 ms
     */
    public FrameClock(LongSupplier nanoClock, Parker parker, long spinTailNanos) {
        if (spinTailNanos < 0) {
            throw new IllegalArgumentException("spinTailNanos must be >= 0");
        }
        this.nanoClock = nanoClock;
        this.parker = parker;
        this.spinTailNanos = spinTailNanos;
    }

    /** Whether pacing should run for the given settings. */
    public static boolean shouldPace(int targetFps, boolean vsyncEnabled) {
        return targetFps > 0 && !vsyncEnabled;
    }

    /** Target frame interval in nanos for a frame rate, or 0 for unlimited. */
    public static long intervalNanosFor(int targetFps) {
        if (targetFps <= 0) {
            return 0L;
        }
        return Math.round(NANOS_PER_SECOND / targetFps);
    }

    /**
     * The deadline for the next frame's presentation.
     *
     * <p>Normally {@code previousDeadline + interval}. If the renderer has fallen more than
     * one interval behind (a real stall), the backlog is dropped and the schedule rebased to
     * {@code now + interval} so recovery is smooth instead of a burst of catch-up frames.
     */
    public static long computeNextDeadline(long previousDeadline, long now, long intervalNanos) {
        long ideal = previousDeadline + intervalNanos;
        if (now - ideal > intervalNanos) {
            return now + intervalNanos;
        }
        return ideal;
    }

    /**
     * How long to park (vs spin) given the nanos remaining until the deadline and the spin
     * tail. Returns 0 when the remaining time is within the spin tail (spin only) or already
     * past the deadline.
     */
    public static long parkBudget(long remainingNanos, long spinTailNanos) {
        if (remainingNanos <= spinTailNanos) {
            return 0L;
        }
        return remainingNanos - spinTailNanos;
    }

    /**
     * Paces the current frame to the target rate. Call once per frame after rendering.
     * No-op (just rebases the schedule) when pacing is disabled.
     */
    public void pace(int targetFps, boolean vsyncEnabled) {
        long now = nanoClock.getAsLong();
        if (!shouldPace(targetFps, vsyncEnabled)) {
            primed = false;
            return;
        }
        long interval = intervalNanosFor(targetFps);
        if (!primed) {
            nextDeadline = now + interval;
            primed = true;
            return;
        }
        long deadline = computeNextDeadline(nextDeadline, now, interval);
        waitUntil(deadline);
        nextDeadline = deadline;
    }

    private void waitUntil(long deadline) {
        long remaining = deadline - nanoClock.getAsLong();
        long park = parkBudget(remaining, spinTailNanos);
        if (park > 0) {
            parker.parkNanos(park);
        }
        // Spin the short tail for accuracy.
        while (nanoClock.getAsLong() < deadline) {
            Thread.onSpinWait();
        }
    }

    /** Forces the next {@link #pace} call to rebase the schedule (e.g. after a settings change). */
    public void reset() {
        primed = false;
    }

    /** Visible for tests: the currently scheduled next-frame deadline. */
    public long currentDeadline() {
        return nextDeadline;
    }
}
