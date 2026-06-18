package xyz.atomland.atomized.config;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomizedConfigTest {

    private record StubModule(String id, boolean defaultEnabled) implements AtomizedModule {
        @Override
        public void init(ModuleContext context) {
        }

        @Override
        public Set<String> conflictsWith() {
            return Set.of();
        }
    }

    private static final StubModule ON = new StubModule("on_by_default", true);
    private static final StubModule OFF = new StubModule("off_by_default", false);

    @Test
    void defaultsFollowModuleDefaults() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON, OFF));
        assertTrue(config.isEnabled("on_by_default", true));
        assertFalse(config.isEnabled("off_by_default", false));
    }

    @Test
    void unknownModuleFallsBackToCallerDefault() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of());
        assertTrue(config.isEnabled("ghost", true));
        assertFalse(config.isEnabled("ghost", false));
        assertTrue(config.isEnabled("ghost"));
    }

    @Test
    void setEnabledOverridesDefault() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON));
        config.setEnabled("on_by_default", false);
        assertFalse(config.isEnabled("on_by_default", true));
        config.setEnabled("on_by_default", true);
        assertTrue(config.isEnabled("on_by_default", false));
    }

    @Test
    void setEnabledCreatesUnknownModuleEntry() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of());
        config.setEnabled("new_module", false);
        assertFalse(config.isEnabled("new_module", true));
        assertTrue(config.moduleIds().contains("new_module"));
    }

    @Test
    void typedOptionRoundTrips() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON));
        config.set("on_by_default", "budget", 4096);
        config.set("on_by_default", "scale", 0.75);
        config.set("on_by_default", "fancy", true);
        config.set("on_by_default", "mode", "soft");

        assertEquals(4096, config.getInt("on_by_default", "budget", -1));
        assertEquals(0.75, config.getDouble("on_by_default", "scale", -1));
        assertTrue(config.getBoolean("on_by_default", "fancy", false));
        assertEquals("soft", config.getString("on_by_default", "mode", "?"));
    }

    @Test
    void missingOptionReturnsFallback() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON));
        assertEquals(42, config.getInt("on_by_default", "missing", 42));
        assertEquals(1.5, config.getDouble("on_by_default", "missing", 1.5));
        assertTrue(config.getBoolean("on_by_default", "missing", true));
        assertEquals("d", config.getString("on_by_default", "missing", "d"));
    }

    @Test
    void missingModuleReturnsFallbackForOptions() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of());
        assertEquals(7, config.getInt("ghost", "key", 7));
    }

    @Test
    void wrongTypeReturnsFallbackInsteadOfThrowing() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON));
        config.set("on_by_default", "budget", "not-a-number");
        assertEquals(9, config.getInt("on_by_default", "budget", 9));
        assertEquals(2.5, config.getDouble("on_by_default", "budget", 2.5));
        assertFalse(config.getBoolean("on_by_default", "budget", false));

        config.set("on_by_default", "flag", 1);
        assertTrue(config.getBoolean("on_by_default", "flag", true));
        assertEquals("s", config.getString("on_by_default", "flag", "s"));
    }

    @Test
    void jsonRoundTripPreservesEverything() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON, OFF));
        config.setEnabled("on_by_default", false);
        config.set("on_by_default", "budget", 2048);
        config.set("off_by_default", "mode", "aggressive");

        AtomizedConfig reloaded = AtomizedConfig.fromJson(config.toJson(), List.of(ON, OFF));
        assertFalse(reloaded.isEnabled("on_by_default", true));
        assertFalse(reloaded.isEnabled("off_by_default", false));
        assertEquals(2048, reloaded.getInt("on_by_default", "budget", -1));
        assertEquals("aggressive", reloaded.getString("off_by_default", "mode", "?"));
    }

    @Test
    void serializedFormCarriesSchemaVersion() {
        JsonObject json = AtomizedConfig.defaults(List.of(ON)).toJson();
        assertEquals(AtomizedConfig.CURRENT_VERSION, json.get("config_version").getAsInt());
        assertTrue(json.has("modules"));
    }

    @Test
    void unknownModuleEntriesArePreservedThroughRoundTrip() {
        // A config written by a newer/older build with extra modules must not lose them.
        JsonObject root = JsonParser.parseString("""
                { "config_version": 1, "modules": {
                    "future_module": { "enabled": false, "options": { "x": 1 } }
                } }
                """).getAsJsonObject();
        AtomizedConfig config = AtomizedConfig.fromJson(root, List.of(ON));

        assertFalse(config.isEnabled("future_module", true));
        assertEquals(1, config.getInt("future_module", "x", -1));

        JsonObject rewritten = config.toJson();
        assertTrue(rewritten.getAsJsonObject("modules").has("future_module"));
        assertEquals(1, rewritten.getAsJsonObject("modules").getAsJsonObject("future_module")
                .getAsJsonObject("options").get("x").getAsInt());
    }

    @Test
    void registeredModuleAbsentFromFileGetsItsDefault() {
        JsonObject root = JsonParser.parseString("{ \"config_version\": 1, \"modules\": {} }").getAsJsonObject();
        AtomizedConfig config = AtomizedConfig.fromJson(root, List.of(ON, OFF));
        assertTrue(config.isEnabled("on_by_default", true));
        assertFalse(config.isEnabled("off_by_default", false));
    }

    @Test
    void malformedModuleEntryIsIgnored() {
        JsonObject root = JsonParser.parseString("""
                { "config_version": 1, "modules": {
                    "on_by_default": "this-should-be-an-object",
                    "off_by_default": { "enabled": "not-a-bool" }
                } }
                """).getAsJsonObject();
        AtomizedConfig config = AtomizedConfig.fromJson(root, List.of(ON, OFF));
        // Both fall back to module defaults.
        assertTrue(config.isEnabled("on_by_default", true));
        assertFalse(config.isEnabled("off_by_default", false));
    }

    @Test
    void missingModulesObjectYieldsDefaults() {
        JsonObject root = JsonParser.parseString("{ \"config_version\": 1 }").getAsJsonObject();
        AtomizedConfig config = AtomizedConfig.fromJson(root, List.of(ON, OFF));
        assertTrue(config.isEnabled("on_by_default", true));
        assertFalse(config.isEnabled("off_by_default", false));
    }

    @Test
    void moduleIdsReflectsKnownModules() {
        AtomizedConfig config = AtomizedConfig.defaults(List.of(ON, OFF));
        assertEquals(List.of("on_by_default", "off_by_default"), List.copyOf(config.moduleIds()));
    }
}
