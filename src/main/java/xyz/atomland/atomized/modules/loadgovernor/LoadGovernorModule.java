package xyz.atomland.atomized.modules.loadgovernor;

import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleIds;

/**
 * {@code load_governor} module (master plan §6.4): the FPS-drop insurance. Mixin-free —
 * it only nudges vanilla options (entity distance scaling, particle budget) through the
 * options API, so its conflict risk is near zero. Holds the {@link LoadGovernor} controller
 * whose hysteresis math is unit-tested.
 */
public final class LoadGovernorModule implements AtomizedModule {
    public static final int DEFAULT_TARGET_FPS = 60;

    private LoadGovernor governor;

    @Override
    public String id() {
        return ModuleIds.LOAD_GOVERNOR;
    }

    @Override
    public void init(ModuleContext context) {
        int targetFps = context.config().getInt(id(), "target_fps", DEFAULT_TARGET_FPS);
        this.governor = LoadGovernor.softProfile(Math.max(1, targetFps));
    }

    @Override
    public void onConfigChange(AtomizedConfig config) {
        int targetFps = config.getInt(id(), "target_fps", DEFAULT_TARGET_FPS);
        this.governor = LoadGovernor.softProfile(Math.max(1, targetFps));
    }

    public LoadGovernor governor() {
        return governor;
    }
}
