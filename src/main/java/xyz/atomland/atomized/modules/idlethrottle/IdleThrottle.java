package xyz.atomland.atomized.modules.idlethrottle;

/**
 * Decides the frame-rate cap based on window focus (master plan §6.5). When the window is
 * inactive (alt-tabbed away), the client wastes CPU/GPU rendering frames nobody sees; this
 * caps the rate so a backgrounded game sips resources.
 *
 * <p>Pure and version-independent: it takes the focus state and the current time and returns
 * a target FPS (0 = uncapped). A short grace period after focus loss avoids throttling brief
 * alt-tabs, so quickly clicking back is jank-free. The window-focus query and the actual
 * pacing live in the module/mixin; this is the testable policy.
 */
public final class IdleThrottle {
    /** Uncapped sentinel — let normal pacing/vanilla handle the frame rate. */
    public static final int UNCAPPED = 0;

    private final int inactiveFps;
    private final long graceNanos;

    private boolean inactiveStarted;
    private long inactiveSinceNanos;

    /**
     * @param inactiveFps cap to apply while the window is inactive (must be &gt; 0)
     * @param graceNanos how long the window must stay inactive before throttling kicks in
     */
    public IdleThrottle(int inactiveFps, long graceNanos) {
        if (inactiveFps <= 0) {
            throw new IllegalArgumentException("inactiveFps must be positive");
        }
        if (graceNanos < 0) {
            throw new IllegalArgumentException("graceNanos must be >= 0");
        }
        this.inactiveFps = inactiveFps;
        this.graceNanos = graceNanos;
    }

    /**
     * @param windowActive whether the game window currently has focus
     * @param nowNanos monotonic timestamp
     * @return the FPS cap to enforce this frame, or {@link #UNCAPPED}
     */
    public int targetFps(boolean windowActive, long nowNanos) {
        if (windowActive) {
            inactiveStarted = false;
            return UNCAPPED;
        }
        if (!inactiveStarted) {
            inactiveStarted = true;
            inactiveSinceNanos = nowNanos;
        }
        return (nowNanos - inactiveSinceNanos) >= graceNanos ? inactiveFps : UNCAPPED;
    }

    public boolean isThrottling(boolean windowActive, long nowNanos) {
        return targetFps(windowActive, nowNanos) != UNCAPPED;
    }

    public int inactiveFps() {
        return inactiveFps;
    }
}
