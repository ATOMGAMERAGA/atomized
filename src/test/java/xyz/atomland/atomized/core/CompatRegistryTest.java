package xyz.atomland.atomized.core;

import java.io.StringReader;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatRegistryTest {

    private static final String RULES = """
            {
              "schema_version": 1,
              "rules": [
                { "mod": "entityculling", "name": "EntityCulling", "disables": ["smart_culling"] },
                { "mod": "moreculling", "name": "MoreCulling", "disables": ["smart_culling"] },
                { "mod": "dynamic_fps", "name": "Dynamic FPS", "disables": ["idle_throttle"] },
                { "mod": "c2me", "name": "C2ME", "disables": ["chunk_smooth"] },
                { "mod": "vulkanmod", "name": "VulkanMod",
                  "disables": ["frame_pacing", "smart_culling", "particle_control", "gui_opt", "chunk_smooth"] },
                { "mod": "immediatelyfast", "name": "ImmediatelyFast",
                  "restricts": { "gui_opt": ["hud_cache", "screen_background_cache"], "memory_relief": ["hud_cache"] } },
                { "mod": "memoryleakfix", "name": "Memory Leak Fix",
                  "restricts": { "memory_relief": ["leak_patch"] } }
              ]
            }
            """;

    private static CompatRegistry registry(String... loadedMods) {
        Set<String> loaded = Set.of(loadedMods);
        return CompatRegistry.fromJson(new StringReader(RULES), loaded::contains);
    }

    @ParameterizedTest
    @CsvSource({
            "entityculling, smart_culling",
            "moreculling, smart_culling",
            "dynamic_fps, idle_throttle",
            "c2me, chunk_smooth",
            "vulkanmod, frame_pacing",
            "vulkanmod, smart_culling",
            "vulkanmod, particle_control",
            "vulkanmod, gui_opt",
            "vulkanmod, chunk_smooth"
    })
    void loadedModDisablesModule(String modId, String moduleId) {
        assertEquals(Optional.of(modId), registry(modId).disabledBy(moduleId));
    }

    @ParameterizedTest
    @CsvSource({
            "entityculling, smart_culling",
            "dynamic_fps, idle_throttle",
            "c2me, chunk_smooth",
            "vulkanmod, frame_pacing"
    })
    void absentModDisablesNothing(String modId, String moduleId) {
        assertEquals(Optional.empty(), registry(/* nothing loaded */).disabledBy(moduleId));
    }

    @ParameterizedTest
    @CsvSource({
            "entityculling, idle_throttle",
            "dynamic_fps, smart_culling",
            "immediatelyfast, gui_opt",
            "memoryleakfix, memory_relief"
    })
    void unrelatedOrRestrictOnlyModsDoNotDisable(String modId, String moduleId) {
        assertEquals(Optional.empty(), registry(modId).disabledBy(moduleId));
    }

    @Test
    void restrictionsAreReportedPerModule() {
        CompatRegistry compat = registry("immediatelyfast");
        assertEquals(Set.of("hud_cache", "screen_background_cache"), compat.restrictedSubFeatures("gui_opt"));
        assertEquals(Set.of("hud_cache"), compat.restrictedSubFeatures("memory_relief"));
        assertEquals(Set.of(), compat.restrictedSubFeatures("smart_culling"));
    }

    @Test
    void restrictionsUnionAcrossLoadedMods() {
        CompatRegistry compat = registry("immediatelyfast", "memoryleakfix");
        assertEquals(Set.of("hud_cache", "leak_patch"), compat.restrictedSubFeatures("memory_relief"));
    }

    @Test
    void restrictionsEmptyWhenModAbsent() {
        assertEquals(Set.of(), registry().restrictedSubFeatures("gui_opt"));
    }

    @Test
    void firstMatchingRuleWins() {
        CompatRegistry compat = registry("entityculling", "moreculling");
        assertEquals(Optional.of("entityculling"), compat.disabledBy("smart_culling"));
    }

    @Test
    void displayNameFallsBackToModId() {
        CompatRegistry compat = registry("entityculling");
        assertEquals("EntityCulling", compat.displayName("entityculling"));
        assertEquals("unknownmod", compat.displayName("unknownmod"));
    }

    @Test
    void moduleDeclaredConflictsAreMerged() {
        CompatRegistry compat = registry(); // no data-rule matches
        Predicate<String> isLoaded = Set.of("custommod")::contains;
        assertEquals(Optional.of("custommod"),
                compat.disabledBy("my_module", Set.of("custommod"), isLoaded));
        assertEquals(Optional.empty(),
                compat.disabledBy("my_module", Set.of("absentmod"), isLoaded));
    }

    @Test
    void dataRulesTakePrecedenceOverDeclaredConflicts() {
        CompatRegistry compat = registry("entityculling");
        Predicate<String> isLoaded = Set.of("entityculling", "other")::contains;
        assertEquals(Optional.of("entityculling"),
                compat.disabledBy("smart_culling", Set.of("other"), isLoaded));
    }

    @Test
    void activeRulesOnlyContainLoadedMods() {
        CompatRegistry compat = registry("c2me", "immediatelyfast");
        assertEquals(2, compat.activeRules().size());
        assertTrue(compat.activeRules().stream().anyMatch(r -> r.modId().equals("c2me")));
        assertTrue(compat.activeRules().stream().anyMatch(r -> r.modId().equals("immediatelyfast")));
    }

    @Test
    void malformedRuleIsSkippedOthersSurvive() {
        String json = """
                { "rules": [
                  { "mod": "broken", "disables": "not-an-array" },
                  { "this_rule_has_no_mod_key": true },
                  { "mod": "entityculling", "disables": ["smart_culling"] }
                ] }
                """;
        CompatRegistry compat = CompatRegistry.fromJson(new StringReader(json), id -> true);
        assertEquals(Optional.of("entityculling"), compat.disabledBy("smart_culling"));
        assertEquals(1, compat.activeRules().size());
    }

    @Test
    void completelyBrokenJsonYieldsEmptyRegistry() {
        CompatRegistry compat = CompatRegistry.fromJson(new StringReader("{{{ not json"), id -> true);
        assertEquals(Optional.empty(), compat.disabledBy("smart_culling"));
        assertEquals(0, compat.activeRules().size());
    }

    @Test
    void missingRulesArrayYieldsEmptyRegistry() {
        CompatRegistry compat = CompatRegistry.fromJson(new StringReader("{\"schema_version\":1}"), id -> true);
        assertEquals(0, compat.activeRules().size());
    }

    @Test
    void emptyRegistryGatesNothing() {
        CompatRegistry compat = CompatRegistry.empty();
        assertEquals(Optional.empty(), compat.disabledBy("smart_culling"));
        assertEquals(Set.of(), compat.restrictedSubFeatures("gui_opt"));
    }

    @Test
    void bundledRulesFileLoadsAndMatchesPlanMatrix() {
        // Loads the real resource shipped in the jar with every overlap mod "installed".
        CompatRegistry compat = CompatRegistry.load(id -> true);
        assertEquals(Optional.of("entityculling"), compat.disabledBy("smart_culling"));
        assertEquals(Optional.of("dynamic_fps"), compat.disabledBy("idle_throttle"));
        assertEquals(Optional.of("c2me"), compat.disabledBy("chunk_smooth"));
        assertTrue(compat.restrictedSubFeatures("gui_opt").contains("hud_cache"));
        assertTrue(compat.restrictedSubFeatures("memory_relief").contains("leak_patch"));
        assertTrue(compat.restrictedSubFeatures("particle_control").contains("ticking_skip"));
        assertTrue(compat.restrictedSubFeatures("sp_boost").contains("autosave_smoothing"));
    }

    @Test
    void bundledRulesNeverDisableDiagnosticsOrLoadGovernor() {
        // §6.4 and §6.10: these modules have no compat gates by design.
        CompatRegistry compat = CompatRegistry.load(id -> true);
        assertEquals(Optional.empty(), compat.disabledBy("diagnostics"));
        assertEquals(Optional.empty(), compat.disabledBy("load_governor"));
    }
}
