package xyz.atomland.atomized.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void versionOfReadsExplicitVersion() {
        assertEquals(1, ConfigMigrations.versionOf(json("{\"config_version\": 1}")));
        assertEquals(7, ConfigMigrations.versionOf(json("{\"config_version\": 7}")));
    }

    @Test
    void missingVersionCountsAsZero() {
        assertEquals(0, ConfigMigrations.versionOf(json("{}")));
    }

    @Test
    void nonNumericVersionCountsAsZero() {
        assertEquals(0, ConfigMigrations.versionOf(json("{\"config_version\": \"one\"}")));
    }

    @Test
    void v0FlatBooleansAreWrappedIntoObjects() {
        JsonObject root = json("""
                { "modules": { "frame_pacing": true, "gui_opt": false } }
                """);
        ConfigMigrations.migrate(root);

        assertEquals(AtomizedConfig.CURRENT_VERSION, root.get("config_version").getAsInt());
        JsonObject modules = root.getAsJsonObject("modules");
        assertTrue(modules.getAsJsonObject("frame_pacing").get("enabled").getAsBoolean());
        assertFalse(modules.getAsJsonObject("gui_opt").get("enabled").getAsBoolean());
    }

    @Test
    void v0ObjectsAreLeftAlone() {
        JsonObject root = json("""
                { "modules": { "frame_pacing": { "enabled": false, "options": { "x": 3 } } } }
                """);
        ConfigMigrations.migrate(root);
        JsonObject entry = root.getAsJsonObject("modules").getAsJsonObject("frame_pacing");
        assertFalse(entry.get("enabled").getAsBoolean());
        assertEquals(3, entry.getAsJsonObject("options").get("x").getAsInt());
    }

    @Test
    void currentVersionIsStampedEvenWithoutModules() {
        JsonObject root = json("{}");
        ConfigMigrations.migrate(root);
        assertEquals(AtomizedConfig.CURRENT_VERSION, root.get("config_version").getAsInt());
    }

    @Test
    void futureVersionIsNotDowngradedDestructively() {
        JsonObject root = json("""
                { "config_version": 99, "modules": { "exotic": { "enabled": true } } }
                """);
        ConfigMigrations.migrate(root);
        // Content untouched; the document is loaded as-is (readable parts only).
        assertTrue(root.getAsJsonObject("modules").getAsJsonObject("exotic").get("enabled").getAsBoolean());
    }

    @Test
    void migrationIsIdempotent() {
        JsonObject root = json("{ \"modules\": { \"a\": true } }");
        ConfigMigrations.migrate(root);
        String once = root.toString();
        ConfigMigrations.migrate(root);
        assertEquals(once, root.toString());
    }
}
