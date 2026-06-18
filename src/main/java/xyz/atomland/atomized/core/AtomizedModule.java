package xyz.atomland.atomized.core;

import java.util.Set;

import xyz.atomland.atomized.config.AtomizedConfig;

/**
 * A single, independently toggleable optimization module.
 *
 * <p>Modules are registered with the {@link ModuleManager}, which gates them through
 * the user config, the {@link CompatRegistry} (conflicting mods) and the
 * {@link VersionGuard} (Minecraft version range) before calling {@link #init}.
 *
 * <p>Contract (master plan §4):
 * <ul>
 *   <li>A disabled module must have zero runtime footprint; its mixins are never applied.</li>
 *   <li>{@link #init} may throw — the manager treats any throwable as fail-soft:
 *       the module is disabled and reported via {@link PanicSwitch}, the game never crashes.</li>
 * </ul>
 */
public interface AtomizedModule {
    /** Stable snake_case identifier, e.g. {@code "smart_culling"}. Used in config and lang keys. */
    String id();

    /** Whether the module is part of the default "safe-fast" profile. */
    default boolean defaultEnabled() {
        return true;
    }

    /**
     * Mod ids that force-disable this module when present, in addition to the
     * data-driven rules in {@code compat_rules.json}.
     */
    default Set<String> conflictsWith() {
        return Set.of();
    }

    /**
     * Minecraft version predicate this module supports (see {@link VersionGuard#matches}).
     * Defaults to all supported versions.
     */
    default String minecraftRange() {
        return "*";
    }

    /** Initializes the module. Only called when all gates pass. */
    void init(ModuleContext context) throws Exception;

    /** Called when the config is reloaded or changed at runtime. Never called on gated-off modules. */
    default void onConfigChange(AtomizedConfig config) {
    }

    /** Called once per client tick while the module is enabled. */
    default void tick() {
    }
}
