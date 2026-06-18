package xyz.atomland.atomized.modules.particlecontrol;

/**
 * Per-tick new-particle rate cap (master plan §6.2). Bursts — TNT, witch farms, light
 * floods — spawn thousands of particles in a single tick and tank the frame; this caps how
 * many <em>new</em> particles may be admitted per tick so those spikes are shaved while
 * normal gameplay particles (which never burst that hard) are untouched.
 *
 * <p>Pure and version-independent: the mixin resets the counter at the start of
 * {@code ParticleEngine#tick} and asks {@link #tryAdd()} before each {@code add}. Capping
 * the <em>rate</em> rather than the total avoids reading the engine's internal particle
 * storage, which differs across Minecraft versions.
 *
 * <p>Not thread-safe; the client thread owns it.
 */
public final class ParticleBudget {
    private int maxNewPerTick;
    private int addedThisTick;
    private long droppedTotal;

    public ParticleBudget(int maxNewPerTick) {
        setMaxNewPerTick(maxNewPerTick);
    }

    public void setMaxNewPerTick(int maxNewPerTick) {
        if (maxNewPerTick < 0) {
            throw new IllegalArgumentException("maxNewPerTick must be >= 0");
        }
        this.maxNewPerTick = maxNewPerTick;
    }

    public int maxNewPerTick() {
        return maxNewPerTick;
    }

    /** Called at the start of each engine tick: clears the per-tick allowance. */
    public void beginTick() {
        addedThisTick = 0;
    }

    /**
     * Reserves one slot for a new particle.
     *
     * @return {@code true} if the particle may be added, {@code false} if this tick's budget
     *         is exhausted (the caller should skip it)
     */
    public boolean tryAdd() {
        if (addedThisTick >= maxNewPerTick) {
            droppedTotal++;
            return false;
        }
        addedThisTick++;
        return true;
    }

    public int addedThisTick() {
        return addedThisTick;
    }

    /** Total particles dropped since construction (diagnostics only). */
    public long droppedTotal() {
        return droppedTotal;
    }
}
