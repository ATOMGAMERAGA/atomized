package xyz.atomland.atomized.diagnostics;

import java.util.Arrays;

/**
 * A fixed-capacity ring buffer of recent frame durations (nanoseconds) with cheap
 * summary statistics: average FPS, 1%/0.1% low FPS and frame-time standard deviation
 * (jitter). These are the numbers the diagnostics HUD shows, the {@code /atomized bench}
 * command writes to CSV, and {@code load_governor} reacts to (master plan §6.4, §6.10).
 *
 * <p>"1% low FPS" follows the common frametime-percentile definition: take the slowest
 * 1% of frames by duration and report the FPS of their mean — a measure of how bad the
 * drops are, not just the average.
 *
 * <p>Not thread-safe; the client render thread owns one instance.
 */
public final class FrameTimeStats {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long[] frameNanos;
    private int count;
    private int head;

    public FrameTimeStats(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.frameNanos = new long[capacity];
    }

    /** Records one frame's duration in nanoseconds (must be positive). Oldest is evicted at capacity. */
    public void record(long durationNanos) {
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("frame duration must be positive: " + durationNanos);
        }
        frameNanos[head] = durationNanos;
        head = (head + 1) % frameNanos.length;
        if (count < frameNanos.length) {
            count++;
        }
    }

    public void clear() {
        count = 0;
        head = 0;
    }

    public int sampleCount() {
        return count;
    }

    public int capacity() {
        return frameNanos.length;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** Mean FPS over the buffered frames, or 0 when empty. */
    public double averageFps() {
        if (count == 0) {
            return 0.0;
        }
        double meanNanos = (double) totalNanos() / count;
        return meanNanos <= 0 ? 0.0 : NANOS_PER_SECOND / meanNanos;
    }

    /** Mean frame time in milliseconds, or 0 when empty. */
    public double averageFrameTimeMillis() {
        if (count == 0) {
            return 0.0;
        }
        return (double) totalNanos() / count / 1_000_000.0;
    }

    /** 1% low FPS (mean FPS of the slowest 1% of frames). */
    public double onePercentLowFps() {
        return percentileLowFps(0.01);
    }

    /** 0.1% low FPS (mean FPS of the slowest 0.1% of frames). */
    public double pointOnePercentLowFps() {
        return percentileLowFps(0.001);
    }

    /**
     * Mean FPS of the slowest {@code fraction} of frames (the "x% low" metric).
     *
     * @param fraction in (0, 1], e.g. 0.01 for 1% low
     */
    public double percentileLowFps(double fraction) {
        if (fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be in (0, 1]");
        }
        if (count == 0) {
            return 0.0;
        }
        long[] sorted = sortedAscending();
        // Slowest frames are the largest durations, at the end of the ascending array.
        int sampleSize = Math.max(1, (int) Math.ceil(count * fraction));
        long sum = 0;
        for (int i = count - sampleSize; i < count; i++) {
            sum += sorted[i];
        }
        double meanNanos = (double) sum / sampleSize;
        return meanNanos <= 0 ? 0.0 : NANOS_PER_SECOND / meanNanos;
    }

    /** Standard deviation of frame times in milliseconds (jitter), or 0 when fewer than 2 samples. */
    public double frameTimeStdDevMillis() {
        if (count < 2) {
            return 0.0;
        }
        double meanNanos = (double) totalNanos() / count;
        double sumSq = 0.0;
        for (int i = 0; i < count; i++) {
            double diff = frameNanos[index(i)] - meanNanos;
            sumSq += diff * diff;
        }
        double varianceNanos = sumSq / count;
        return Math.sqrt(varianceNanos) / 1_000_000.0;
    }

    /** Longest buffered frame time in milliseconds (worst spike), or 0 when empty. */
    public double maxFrameTimeMillis() {
        if (count == 0) {
            return 0.0;
        }
        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, frameNanos[index(i)]);
        }
        return max / 1_000_000.0;
    }

    private long totalNanos() {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += frameNanos[index(i)];
        }
        return sum;
    }

    private long[] sortedAscending() {
        long[] copy = new long[count];
        for (int i = 0; i < count; i++) {
            copy[i] = frameNanos[index(i)];
        }
        Arrays.sort(copy);
        return copy;
    }

    /** Maps logical index 0..count-1 (oldest..newest) to the backing array slot. */
    private int index(int logical) {
        int start = (head - count + frameNanos.length) % frameNanos.length;
        return (start + logical) % frameNanos.length;
    }
}
