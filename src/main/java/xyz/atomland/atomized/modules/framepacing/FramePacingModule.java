package xyz.atomland.atomized.modules.framepacing;

import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleIds;

/**
 * {@code frame_pacing} module (master plan §6.3): the heart of smoothness. Owns the
 * {@link FrameClock} that evens out frame intervals. Auto-disabled under VulkanMod
 * (declared in the compat rules); a per-target mixin routes the vanilla limiter through
 * the clock once wired.
 *
 * <p>The clock's timing math is unit-tested; this module is the config-bound holder that
 * exposes a single shared clock to the mixin layer.
 */
public final class FramePacingModule implements AtomizedModule {
    /** Spin-tail length absorbing park oversleep; configurable for unusual schedulers. */
    public static final long DEFAULT_SPIN_TAIL_NANOS = 1_500_000L;

    private FrameClock clock;
    private boolean smoothingWindow;

    @Override
    public String id() {
        return ModuleIds.FRAME_PACING;
    }

    @Override
    public void init(ModuleContext context) {
        long spinTail = context.config().getInt(id(), "spin_tail_micros",
                (int) (DEFAULT_SPIN_TAIL_NANOS / 1000)) * 1000L;
        this.clock = new FrameClock(System::nanoTime,
                java.util.concurrent.locks.LockSupport::parkNanos, Math.max(0, spinTail));
        this.smoothingWindow = context.config().getBoolean(id(), "smoothing_window", false);
    }

    @Override
    public void onConfigChange(AtomizedConfig config) {
        if (clock != null) {
            clock.reset();
        }
        this.smoothingWindow = config.getBoolean(id(), "smoothing_window", smoothingWindow);
    }

    public FrameClock clock() {
        return clock;
    }

    public boolean isSmoothingWindowEnabled() {
        return smoothingWindow;
    }
}
