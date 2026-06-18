package xyz.atomland.atomized.modules.loadgovernor;

/**
 * Dynamic load governor controller (master plan §6.4): when frame time rises above target
 * (a drop), it temporarily and <em>gradually</em> trims quality — entity render distance,
 * particle budget, decorative animation rate — then restores it gradually once the load
 * passes. The quality loss lasts split-seconds and is hard to notice, while the drops are
 * shaved down.
 *
 * <p>This is the pure controller (no mixins, no Minecraft types — the module applies the
 * output through the vanilla options API, master plan §6.4). It emits a single throttle
 * <em>level</em> in {@code [0, 1]} (0 = full quality, 1 = maximum reduction) governed by
 * hysteresis so it does not oscillate:
 * <ul>
 *   <li>frame time above {@code target * highRatio} → ramp the level up (react fast);</li>
 *   <li>frame time below {@code target * lowRatio} → ramp the level down (recover slowly);</li>
 *   <li>in between → hold (the hysteresis band that prevents flapping).</li>
 * </ul>
 */
public final class LoadGovernor {
    private final double targetFrameTimeMillis;
    private final double highRatio;
    private final double lowRatio;
    private final double rampUpStep;
    private final double rampDownStep;
    private final double minEntityScale;
    private final double minParticleScale;

    private double level;

    /** A conservative "soft" default profile (master plan §6.4 default). */
    public static LoadGovernor softProfile(int targetFps) {
        return new LoadGovernor(targetFps, 1.2, 0.9, 0.20, 0.05, 0.6, 0.5);
    }

    /**
     * @param targetFps the frame rate the governor protects (must be positive)
     * @param highRatio frame-time multiple of target above which quality is reduced (&gt; 1)
     * @param lowRatio frame-time multiple of target below which quality is restored (&lt; 1)
     * @param rampUpStep per-update level increase when overloaded (react fast), in (0, 1]
     * @param rampDownStep per-update level decrease when recovered (gradual), in (0, 1]
     * @param minEntityScale floor for entity render-distance scaling at full throttle, in (0, 1]
     * @param minParticleScale floor for particle-budget scaling at full throttle, in (0, 1]
     */
    public LoadGovernor(int targetFps, double highRatio, double lowRatio, double rampUpStep,
                        double rampDownStep, double minEntityScale, double minParticleScale) {
        if (targetFps <= 0) {
            throw new IllegalArgumentException("targetFps must be positive");
        }
        if (highRatio <= 1.0 || lowRatio >= 1.0 || lowRatio <= 0.0) {
            throw new IllegalArgumentException("require lowRatio in (0,1) < 1 < highRatio");
        }
        if (rampUpStep <= 0 || rampUpStep > 1 || rampDownStep <= 0 || rampDownStep > 1) {
            throw new IllegalArgumentException("ramp steps must be in (0, 1]");
        }
        if (minEntityScale <= 0 || minEntityScale > 1 || minParticleScale <= 0 || minParticleScale > 1) {
            throw new IllegalArgumentException("min scales must be in (0, 1]");
        }
        this.targetFrameTimeMillis = 1000.0 / targetFps;
        this.highRatio = highRatio;
        this.lowRatio = lowRatio;
        this.rampUpStep = rampUpStep;
        this.rampDownStep = rampDownStep;
        this.minEntityScale = minEntityScale;
        this.minParticleScale = minParticleScale;
    }

    /**
     * Feeds one frame-time sample and returns the new throttle level.
     *
     * @param frameTimeMillis the latest (typically smoothed) frame time
     * @return the updated level in [0, 1]
     */
    public double update(double frameTimeMillis) {
        double high = targetFrameTimeMillis * highRatio;
        double low = targetFrameTimeMillis * lowRatio;
        if (frameTimeMillis > high) {
            level = Math.min(1.0, level + rampUpStep);
        } else if (frameTimeMillis < low) {
            level = Math.max(0.0, level - rampDownStep);
        }
        // Within [low, high]: hold (hysteresis band).
        return level;
    }

    /** Current throttle level in [0, 1] (0 = full quality). */
    public double level() {
        return level;
    }

    public boolean isThrottling() {
        return level > 0.0;
    }

    /** Resets to full quality (e.g. on world change or when the module is toggled). */
    public void reset() {
        level = 0.0;
    }

    /** Entity render-distance scale to apply now: 1.0 at full quality down to {@code minEntityScale}. */
    public double entityDistanceScale() {
        return lerp(1.0, minEntityScale, level);
    }

    /** Particle-budget scale to apply now: 1.0 at full quality down to {@code minParticleScale}. */
    public double particleBudgetScale() {
        return lerp(1.0, minParticleScale, level);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
