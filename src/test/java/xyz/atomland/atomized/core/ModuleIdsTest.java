package xyz.atomland.atomized.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleIdsTest {

    @Test
    void allTenPlannedModulesArePresent() {
        assertEquals(10, ModuleIds.ALL.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "smart_culling", "particle_control", "frame_pacing", "load_governor", "idle_throttle",
            "memory_relief", "gui_opt", "chunk_smooth", "sp_boost", "diagnostics"
    })
    void planSection6IdsExist(String moduleId) {
        assertTrue(ModuleIds.ALL.contains(moduleId));
    }

    @Test
    void safeFastProfileDefaultsAreAllOnInitially() {
        // Master plan §6: every wave-1/wave-2 module ships default-ON (HUD sub-feature
        // of diagnostics is off, but the module itself is on).
        ModuleIds.ALL.forEach(id -> assertTrue(ModuleIds.defaultEnabled(id), id));
    }

    @Test
    void unknownModuleDefaultsToEnabled() {
        assertTrue(ModuleIds.defaultEnabled("unknown_future_module"));
    }
}
