package xyz.atomland.atomized.core;

import xyz.atomland.atomized.config.AtomizedConfig;

/**
 * Everything a module may touch during {@link AtomizedModule#init}.
 *
 * <p>Kept deliberately small and Minecraft-free so modules' gating logic stays unit-testable.
 *
 * @param config the active configuration
 * @param compat conflict/restriction decisions for loaded mods
 */
public record ModuleContext(AtomizedConfig config, CompatRegistry compat) {

    /**
     * Whether a sub-feature of a module is allowed to run. A sub-feature is restricted
     * when a partially overlapping mod is loaded (e.g. ImmediatelyFast restricts
     * {@code gui_opt}'s HUD cache while the rest of the module keeps working).
     */
    public boolean isSubFeatureAllowed(String moduleId, String subFeature) {
        return !compat.restrictedSubFeatures(moduleId).contains(subFeature);
    }
}
