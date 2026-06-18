package xyz.atomland.atomized.modules.particlecontrol;

import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleIds;

/**
 * {@code particle_control} module (master plan §6.2). Holds the per-tick {@link ParticleBudget}
 * and exposes it statically to the {@code ParticleEngine} mixin. Targets {@code ParticleEngine},
 * which is untouched by Sodium and every other scanned mod — a fully clean injection site.
 */
public final class ParticleControlModule implements AtomizedModule {
    /** Generous default: only genuine bursts (TNT/witch farms) exceed it (master plan §6.2). */
    public static final int DEFAULT_MAX_NEW_PER_TICK = 2048;

    private static volatile ParticleControlModule active;

    private ParticleBudget budget;

    /** The running module, or {@code null} when disabled/not initialized. */
    public static ParticleControlModule active() {
        return active;
    }

    @Override
    public String id() {
        return ModuleIds.PARTICLE_CONTROL;
    }

    @Override
    public void init(ModuleContext context) {
        int max = context.config().getInt(id(), "max_new_per_tick", DEFAULT_MAX_NEW_PER_TICK);
        this.budget = new ParticleBudget(Math.max(0, max));
        active = this;
    }

    @Override
    public void onConfigChange(AtomizedConfig config) {
        if (budget != null) {
            budget.setMaxNewPerTick(Math.max(0, config.getInt(id(), "max_new_per_tick", DEFAULT_MAX_NEW_PER_TICK)));
        }
    }

    public ParticleBudget budget() {
        return budget;
    }
}
