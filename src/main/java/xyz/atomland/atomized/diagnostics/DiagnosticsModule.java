package xyz.atomland.atomized.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleIds;

/**
 * {@code diagnostics} module (master plan §6.10): owns the frametime statistics that feed
 * the HUD, the {@code /atomized bench} command and {@code load_governor}. Always safe — no
 * compat gate — so it can be relied on as the measurement backbone.
 *
 * <p>This holds the version-independent measurement core. Per-frame sampling is fed in by
 * the diagnostics mixin (gated to this module) once wired; the statistics object and its
 * arithmetic are unit-tested independently.
 */
public final class DiagnosticsModule implements AtomizedModule {
    /** Default ring-buffer size: ~17 s at 60 FPS, enough for stable 1%/0.1% lows. */
    public static final int DEFAULT_SAMPLE_WINDOW = 1024;
    /** A frame slower than this (ms) is logged by the lag-spike logger (master plan §6.10b). */
    public static final double DEFAULT_SPIKE_THRESHOLD_MS = 50.0;

    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/Diagnostics");
    /** Minimum gap between lag-spike log lines, so a stutter storm cannot spam the log. */
    private static final long SPIKE_LOG_COOLDOWN_NANOS = 3_000_000_000L;

    /** The live instance, so the frame-sampling mixin can feed it without plumbing. */
    private static volatile DiagnosticsModule active;

    private FrameTimeStats stats;
    private boolean hudVisible;
    private double spikeThresholdMs = DEFAULT_SPIKE_THRESHOLD_MS;
    private long lastSpikeLogNanos;

    /** The running diagnostics module, or {@code null} if disabled/not yet initialized. */
    public static DiagnosticsModule active() {
        return active;
    }

    @Override
    public String id() {
        return ModuleIds.DIAGNOSTICS;
    }

    @Override
    public void init(ModuleContext context) {
        int window = context.config().getInt(id(), "sample_window", DEFAULT_SAMPLE_WINDOW);
        this.stats = new FrameTimeStats(Math.max(16, window));
        // HUD ships off (master plan §6.10 default); the rest stays ready.
        this.hudVisible = context.config().getBoolean(id(), "hud_visible", false);
        this.spikeThresholdMs = context.config().getDouble(id(), "spike_threshold_ms", DEFAULT_SPIKE_THRESHOLD_MS);
        active = this;
    }

    @Override
    public void onConfigChange(AtomizedConfig config) {
        this.hudVisible = config.getBoolean(id(), "hud_visible", hudVisible);
        this.spikeThresholdMs = config.getDouble(id(), "spike_threshold_ms", spikeThresholdMs);
    }

    /** Records a frame duration (nanoseconds) into the statistics window and logs spikes. */
    public void recordFrame(long durationNanos) {
        if (stats == null || durationNanos <= 0) {
            return;
        }
        stats.record(durationNanos);
        if (isSpike(durationNanos)) {
            logSpike(durationNanos);
        }
    }

    private void logSpike(long durationNanos) {
        long now = System.nanoTime();
        if (now - lastSpikeLogNanos < SPIKE_LOG_COOLDOWN_NANOS) {
            return;
        }
        lastSpikeLogNanos = now;
        try {
            LOGGER.info("Frame spike: {} ms (threshold {} ms). avg {} FPS, 1% low {} FPS.",
                    String.format("%.1f", durationNanos / 1_000_000.0),
                    String.format("%.0f", spikeThresholdMs),
                    String.format("%.0f", stats.averageFps()),
                    String.format("%.0f", stats.onePercentLowFps()));
        } catch (RuntimeException ignored) {
            // Logging must never disturb the render thread.
        }
    }

    /** Whether the given frame duration counts as a lag spike worth logging. */
    public boolean isSpike(long durationNanos) {
        return durationNanos / 1_000_000.0 >= spikeThresholdMs;
    }

    public FrameTimeStats stats() {
        return stats;
    }

    public boolean isHudVisible() {
        return hudVisible;
    }

    public void setHudVisible(boolean visible) {
        this.hudVisible = visible;
    }

    public double spikeThresholdMillis() {
        return spikeThresholdMs;
    }
}
