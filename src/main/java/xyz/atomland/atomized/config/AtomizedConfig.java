package xyz.atomland.atomized.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xyz.atomland.atomized.core.AtomizedModule;

/**
 * In-memory configuration model, serialized to {@code config/atomized.json}.
 *
 * <p>Schema (versioned for migrations):
 * <pre>{@code
 * {
 *   "config_version": 1,
 *   "modules": {
 *     "frame_pacing": { "enabled": true, "options": { "smoothing_window": 3 } }
 *   }
 * }
 * }</pre>
 *
 * <p>Unknown module entries are preserved verbatim so downgrades/upgrades never lose
 * user settings. Reads are tolerant: a missing or wrongly-typed option falls back to
 * the supplied default instead of throwing.
 */
public final class AtomizedConfig {
    public static final int CURRENT_VERSION = 1;

    private final Map<String, ModuleEntry> modules = new LinkedHashMap<>();

    private AtomizedConfig() {
    }

    /** Builds the default "safe-fast" profile from the registered modules. */
    public static AtomizedConfig defaults(Collection<? extends AtomizedModule> registeredModules) {
        AtomizedConfig config = new AtomizedConfig();
        for (AtomizedModule module : registeredModules) {
            config.modules.put(module.id(), new ModuleEntry(module.defaultEnabled()));
        }
        return config;
    }

    /**
     * Whether a module is enabled. Modules unknown to the config (e.g. added after the
     * file was written) fall back to the module's own default.
     */
    public boolean isEnabled(String moduleId, boolean defaultEnabled) {
        ModuleEntry entry = modules.get(moduleId);
        return entry == null ? defaultEnabled : entry.enabled;
    }

    public boolean isEnabled(String moduleId) {
        return isEnabled(moduleId, true);
    }

    public void setEnabled(String moduleId, boolean enabled) {
        modules.computeIfAbsent(moduleId, id -> new ModuleEntry(enabled)).enabled = enabled;
    }

    public int getInt(String moduleId, String key, int fallback) {
        JsonElement value = option(moduleId, key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        return fallback;
    }

    public double getDouble(String moduleId, String key, double fallback) {
        JsonElement value = option(moduleId, key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsDouble();
        }
        return fallback;
    }

    public boolean getBoolean(String moduleId, String key, boolean fallback) {
        JsonElement value = option(moduleId, key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        return fallback;
    }

    public String getString(String moduleId, String key, String fallback) {
        JsonElement value = option(moduleId, key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        return fallback;
    }

    public void set(String moduleId, String key, int value) {
        setOption(moduleId, key, new JsonPrimitive(value));
    }

    public void set(String moduleId, String key, double value) {
        setOption(moduleId, key, new JsonPrimitive(value));
    }

    public void set(String moduleId, String key, boolean value) {
        setOption(moduleId, key, new JsonPrimitive(value));
    }

    public void set(String moduleId, String key, String value) {
        setOption(moduleId, key, new JsonPrimitive(value));
    }

    /** Module ids present in this config, in stable order. */
    public Collection<String> moduleIds() {
        return java.util.List.copyOf(modules.keySet());
    }

    private JsonElement option(String moduleId, String key) {
        ModuleEntry entry = modules.get(moduleId);
        return entry == null ? null : entry.options.get(key);
    }

    private void setOption(String moduleId, String key, JsonElement value) {
        modules.computeIfAbsent(moduleId, id -> new ModuleEntry(true)).options.put(key, value);
    }

    /** Serializes to the canonical JSON form. */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("config_version", CURRENT_VERSION);
        JsonObject modulesObj = new JsonObject();
        for (Map.Entry<String, ModuleEntry> entry : modules.entrySet()) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", entry.getValue().enabled);
            if (!entry.getValue().options.isEmpty()) {
                JsonObject options = new JsonObject();
                entry.getValue().options.forEach(options::add);
                moduleObj.add("options", options);
            }
            modulesObj.add(entry.getKey(), moduleObj);
        }
        root.add("modules", modulesObj);
        return root;
    }

    /**
     * Deserializes an already-migrated JSON document (see {@link ConfigMigrations}).
     * Modules registered but absent from the document get their defaults; per-entry
     * type errors fall back to defaults instead of failing the whole load.
     */
    public static AtomizedConfig fromJson(JsonObject root, Collection<? extends AtomizedModule> registeredModules) {
        AtomizedConfig config = defaults(registeredModules);
        JsonElement modulesElement = root.get("modules");
        if (modulesElement == null || !modulesElement.isJsonObject()) {
            return config;
        }
        JsonObject modulesObj = modulesElement.getAsJsonObject();
        for (String moduleId : modulesObj.keySet()) {
            JsonElement moduleElement = modulesObj.get(moduleId);
            if (!moduleElement.isJsonObject()) {
                continue;
            }
            JsonObject moduleObj = moduleElement.getAsJsonObject();
            ModuleEntry entry = config.modules.computeIfAbsent(moduleId, id -> new ModuleEntry(true));
            JsonElement enabled = moduleObj.get("enabled");
            if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
                entry.enabled = enabled.getAsBoolean();
            }
            JsonElement options = moduleObj.get("options");
            if (options != null && options.isJsonObject()) {
                for (String key : options.getAsJsonObject().keySet()) {
                    entry.options.put(key, options.getAsJsonObject().get(key));
                }
            }
        }
        return config;
    }

    private static final class ModuleEntry {
        boolean enabled;
        final Map<String, JsonElement> options = new LinkedHashMap<>();

        ModuleEntry(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
