package xyz.atomland.atomized.core;

import java.util.Map;
import java.util.Set;

/**
 * Compile-time registry of the planned module ids and their default-enabled state
 * (master plan §6). The mixin plugin runs before modules are registered, so it needs
 * this static knowledge to gate mixin packages of modules whose default is OFF.
 */
public final class ModuleIds {
    public static final String SMART_CULLING = "smart_culling";
    public static final String PARTICLE_CONTROL = "particle_control";
    public static final String FRAME_PACING = "frame_pacing";
    public static final String LOAD_GOVERNOR = "load_governor";
    public static final String IDLE_THROTTLE = "idle_throttle";
    public static final String MEMORY_RELIEF = "memory_relief";
    public static final String GUI_OPT = "gui_opt";
    public static final String CHUNK_SMOOTH = "chunk_smooth";
    public static final String SP_BOOST = "sp_boost";
    public static final String DIAGNOSTICS = "diagnostics";

    /** Module id → part of the default "safe-fast" profile (§5 golden rule 7). */
    public static final Map<String, Boolean> DEFAULT_ENABLED = Map.ofEntries(
            Map.entry(SMART_CULLING, true),
            Map.entry(PARTICLE_CONTROL, true),
            Map.entry(FRAME_PACING, true),
            Map.entry(LOAD_GOVERNOR, true),
            Map.entry(IDLE_THROTTLE, true),
            Map.entry(MEMORY_RELIEF, true),
            Map.entry(GUI_OPT, true),
            Map.entry(CHUNK_SMOOTH, true),
            Map.entry(SP_BOOST, true),
            Map.entry(DIAGNOSTICS, true));

    public static final Set<String> ALL = DEFAULT_ENABLED.keySet();

    private ModuleIds() {
    }

    public static boolean defaultEnabled(String moduleId) {
        return DEFAULT_ENABLED.getOrDefault(moduleId, true);
    }
}
