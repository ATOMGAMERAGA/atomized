package xyz.atomland.atomized.core;

/**
 * The gate decision and runtime state of a module, surfaced to the status screen,
 * the {@code /atomized} command and the logs.
 *
 * @param moduleId the module id
 * @param state the gate/runtime state
 * @param detail human-relevant detail: the conflicting mod id for
 *               {@link ModuleState#DISABLED_CONFLICT}, the required range for
 *               {@link ModuleState#DISABLED_VERSION}, the error summary for
 *               {@link ModuleState#FAILED}; empty otherwise
 */
public record ModuleStatus(String moduleId, ModuleState state, String detail) {

    public static ModuleStatus enabled(String moduleId) {
        return new ModuleStatus(moduleId, ModuleState.ENABLED, "");
    }

    public boolean isEnabled() {
        return state == ModuleState.ENABLED;
    }

    public enum ModuleState {
        /** All gates passed and init succeeded. */
        ENABLED,
        /** Turned off by the user config. */
        DISABLED_CONFIG,
        /** Auto-disabled because an overlapping mod is loaded (master plan golden rule §4). */
        DISABLED_CONFLICT,
        /** The current Minecraft version is outside the module's supported range. */
        DISABLED_VERSION,
        /** init() or tick() threw; the module was shut down fail-soft by the PanicSwitch. */
        FAILED
    }
}
