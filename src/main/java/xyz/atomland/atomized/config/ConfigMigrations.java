package xyz.atomland.atomized.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forward-only config schema migrations. Each step upgrades one version; unknown or
 * future versions are left untouched (the loader then falls back to defaults for
 * anything it cannot read, never destroying the file).
 */
public final class ConfigMigrations {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/Config");

    private ConfigMigrations() {
    }

    /** Reads the schema version; documents predating versioning count as version 0. */
    public static int versionOf(JsonObject root) {
        JsonElement version = root.get("config_version");
        if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
            return version.getAsInt();
        }
        return 0;
    }

    /**
     * Migrates the document in place up to {@link AtomizedConfig#CURRENT_VERSION}.
     *
     * @return the migrated document (same instance)
     */
    public static JsonObject migrate(JsonObject root) {
        int version = versionOf(root);
        if (version > AtomizedConfig.CURRENT_VERSION) {
            LOGGER.warn("Config file has future schema version {} (this build understands {}). "
                    + "Loading what is readable; the file will be rewritten at version {}.",
                    version, AtomizedConfig.CURRENT_VERSION, AtomizedConfig.CURRENT_VERSION);
            return root;
        }
        if (version == 0) {
            migrateV0ToV1(root);
        }
        root.addProperty("config_version", AtomizedConfig.CURRENT_VERSION);
        return root;
    }

    /**
     * v0 → v1: pre-release files stored modules as a flat {@code id → boolean} map;
     * wrap each into the {@code {"enabled": ...}} object form.
     */
    private static void migrateV0ToV1(JsonObject root) {
        JsonElement modulesElement = root.get("modules");
        if (modulesElement == null || !modulesElement.isJsonObject()) {
            return;
        }
        JsonObject modules = modulesElement.getAsJsonObject();
        for (String moduleId : java.util.List.copyOf(modules.keySet())) {
            JsonElement entry = modules.get(moduleId);
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isBoolean()) {
                JsonObject wrapped = new JsonObject();
                wrapped.addProperty("enabled", entry.getAsBoolean());
                modules.add(moduleId, wrapped);
            }
        }
    }
}
