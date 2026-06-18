package xyz.atomland.atomized.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameTimeStatsTest {

    private static final long MS = 1_000_000L; // nanos per millisecond

    @Test
    void emptyBufferReportsZeros() {
        FrameTimeStats stats = new FrameTimeStats(64);
        assertTrue(stats.isEmpty());
        assertEquals(0.0, stats.averageFps());
        assertEquals(0.0, stats.averageFrameTimeMillis());
        assertEquals(0.0, stats.onePercentLowFps());
        assertEquals(0.0, stats.frameTimeStdDevMillis());
        assertEquals(0.0, stats.maxFrameTimeMillis());
    }

    @Test
    void constructorRejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(0));
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(-1));
    }

    @Test
    void recordRejectsNonPositiveDuration() {
        FrameTimeStats stats = new FrameTimeStats(8);
        assertThrows(IllegalArgumentException.class, () -> stats.record(0));
        assertThrows(IllegalArgumentException.class, () -> stats.record(-5));
    }

    @Test
    void steadySixtyFpsAveragesSixty() {
        FrameTimeStats stats = new FrameTimeStats(120);
        for (int i = 0; i < 120; i++) {
            stats.record(16_666_667L); // 1/60 s
        }
        assertEquals(60.0, stats.averageFps(), 0.01);
        assertEquals(16.6667, stats.averageFrameTimeMillis(), 0.001);
        // Perfectly steady -> no jitter.
        assertEquals(0.0, stats.frameTimeStdDevMillis(), 1e-6);
    }

    @Test
    void averageFpsForMixedFrames() {
        FrameTimeStats stats = new FrameTimeStats(4);
        // Two 10ms frames and two 30ms frames: mean frame time 20ms -> 50 FPS.
        stats.record(10 * MS);
        stats.record(30 * MS);
        stats.record(10 * MS);
        stats.record(30 * MS);
        assertEquals(50.0, stats.averageFps(), 0.01);
        assertEquals(20.0, stats.averageFrameTimeMillis(), 0.01);
    }

    @Test
    void ringBufferEvictsOldestBeyondCapacity() {
        FrameTimeStats stats = new FrameTimeStats(3);
        stats.record(100 * MS); // will be evicted
        stats.record(10 * MS);
        stats.record(10 * MS);
        stats.record(10 * MS); // pushes out the 100ms frame
        assertEquals(3, stats.sampleCount());
        assertEquals(100.0, stats.averageFps(), 0.01); // all 10ms now
        assertEquals(10.0, stats.maxFrameTimeMillis(), 0.01);
    }

    @Test
    void countCapsAtCapacity() {
        FrameTimeStats stats = new FrameTimeStats(5);
        for (int i = 0; i < 50; i++) {
            stats.record(16 * MS);
        }
        assertEquals(5, stats.sampleCount());
        assertEquals(5, stats.capacity());
    }

    @Test
    void onePercentLowReflectsWorstFrames() {
        FrameTimeStats stats = new FrameTimeStats(100);
        for (int i = 0; i < 99; i++) {
            stats.record(10 * MS); // 100 FPS frames
        }
        stats.record(100 * MS); // one nasty 10 FPS spike
        // 1% of 100 frames = the single slowest (100ms) -> 10 FPS.
        assertEquals(10.0, stats.onePercentLowFps(), 0.01);
        // Average stays high despite the spike.
        assertTrue(stats.averageFps() > 80.0);
    }

    @Test
    void percentileLowRoundsUpToAtLeastOneFrame() {
        FrameTimeStats stats = new FrameTimeStats(10);
        for (int i = 0; i < 9; i++) {
            stats.record(10 * MS);
        }
        stats.record(50 * MS);
        // 0.1% of 10 frames rounds up to 1 frame = the 50ms one -> 20 FPS.
        assertEquals(20.0, stats.pointOnePercentLowFps(), 0.01);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.5})
    void percentileLowRejectsBadFraction(double fraction) {
        FrameTimeStats stats = new FrameTimeStats(10);
        stats.record(10 * MS);
        assertThrows(IllegalArgumentException.class, () -> stats.percentileLowFps(fraction));
    }

    @Test
    void fullFractionUsesEveryFrame() {
        FrameTimeStats stats = new FrameTimeStats(4);
        stats.record(10 * MS);
        stats.record(30 * MS);
        stats.record(10 * MS);
        stats.record(30 * MS);
        // fraction 1.0 -> all frames -> equals overall average FPS.
        assertEquals(stats.averageFps(), stats.percentileLowFps(1.0), 0.01);
    }

    @Test
    void stdDevCapturesJitter() {
        FrameTimeStats steady = new FrameTimeStats(10);
        FrameTimeStats jittery = new FrameTimeStats(10);
        for (int i = 0; i < 10; i++) {
            steady.record(20 * MS);
        }
        for (int i = 0; i < 10; i++) {
            jittery.record((i % 2 == 0 ? 10 : 30) * MS); // alternating 10/30ms, same mean
        }
        assertEquals(0.0, steady.frameTimeStdDevMillis(), 1e-6);
        // Same 20ms mean but heavy jitter -> stddev 10ms.
        assertEquals(20.0, jittery.averageFrameTimeMillis(), 0.01);
        assertEquals(10.0, jittery.frameTimeStdDevMillis(), 0.01);
    }

    @Test
    void singleSampleHasNoStdDev() {
        FrameTimeStats stats = new FrameTimeStats(10);
        stats.record(16 * MS);
        assertEquals(0.0, stats.frameTimeStdDevMillis());
    }

    @Test
    void maxFrameTimeTracksWorstSpike() {
        FrameTimeStats stats = new FrameTimeStats(10);
        stats.record(10 * MS);
        stats.record(45 * MS);
        stats.record(12 * MS);
        assertEquals(45.0, stats.maxFrameTimeMillis(), 0.01);
    }

    @Test
    void clearResetsEverything() {
        FrameTimeStats stats = new FrameTimeStats(10);
        for (int i = 0; i < 10; i++) {
            stats.record(16 * MS);
        }
        stats.clear();
        assertTrue(stats.isEmpty());
        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.averageFps());
    }

    @Test
    void statisticsCorrectAfterWraparound() {
        // Exercise the ring-index math across a wrap boundary.
        FrameTimeStats stats = new FrameTimeStats(3);
        stats.record(99 * MS);
        stats.record(99 * MS);
        stats.record(99 * MS);
        stats.record(20 * MS);
        stats.record(20 * MS);
        stats.record(20 * MS); // buffer now all 20ms
        assertEquals(50.0, stats.averageFps(), 0.01);
        assertEquals(20.0, stats.maxFrameTimeMillis(), 0.01);
        assertEquals(0.0, stats.frameTimeStdDevMillis(), 1e-6);
    }
}
