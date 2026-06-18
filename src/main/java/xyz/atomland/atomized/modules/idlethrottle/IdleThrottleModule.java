package xyz.atomland.atomized.modules.idlethrottle;

import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleIds;
import xyz.atomland.atomized.modules.framepacing.FrameClock;

/**
 * {@code idle_throttle} module (master plan §6.5): caps the frame rate while the window is
 * unfocused so a backgrounded game stops burning CPU/GPU. Auto-disabled when Dynamic FPS is
 * present (compat rules). Reuses the tested {@link FrameClock} to enforce the cap and the
 * tested {@link IdleThrottle} to decide it.
 */
public final class IdleThrottleModule implements AtomizedModule {
    public static final int DEFAULT_INACTIVE_FPS = 30;
    public static final long DEFAULT_GRACE_NANOS = 500_000_000L;

    private static volatile IdleThrottleModule active;

    private IdleThrottle policy;
    private FrameClock clock;

    public static IdleThrottleModule active() {
        return active;
    }

    @Override
    public String id() {
        return ModuleIds.IDLE_THROTTLE;
    }

    @Override
    public void init(ModuleContext context) {
        rebuild(context.config());
        active = this;
    }

    @Override
    public void onConfigChange(AtomizedConfig config) {
        rebuild(config);
    }

    private void rebuild(AtomizedConfig config) {
        int inactiveFps = Math.max(1, config.getInt(id(), "inactive_fps", DEFAULT_INACTIVE_FPS));
        this.policy = new IdleThrottle(inactiveFps, DEFAULT_GRACE_NANOS);
        this.clock = new FrameClock();
    }

    /**
     * Called at the end of each frame with the current focus state. Paces the frame to the
     * idle cap when unfocused; does nothing while focused (lets normal pacing run).
     */
    public void onFrameEnd(boolean windowActive) {
        IdleThrottle p = policy;
        FrameClock c = clock;
        if (p == null || c == null) {
            return;
        }
        int target = p.targetFps(windowActive, System.nanoTime());
        // pace() is a no-op (and unprimes) when target == 0 (UNCAPPED).
        c.pace(target, false);
    }
}
