package xyz.atomland.atomized.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place where mod-conflict decisions are made (master plan §4, §8).
 *
 * <p>Rules are data, not code: they live in {@code compat_rules.json} on the classpath.
 * A rule either <em>disables</em> whole modules ("territory overlap", e.g. EntityCulling
 * disables {@code smart_culling}) or <em>restricts</em> named sub-features of a module
 * ("partial overlap", e.g. ImmediatelyFast restricts {@code gui_opt}'s HUD cache).
 *
 * <p>Loading is fail-soft: malformed rules are skipped with a warning, and a completely
 * unreadable rules file yields an empty registry — Atomized then simply applies no
 * compat gating rather than crashing the game.
 */
public final class CompatRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/CompatRegistry");
    private static final String RESOURCE = "/atomized_compat_rules.json";

    private final List<Rule> activeRules;

    private CompatRegistry(List<Rule> activeRules) {
        this.activeRules = List.copyOf(activeRules);
    }

    /** Loads the bundled rules and filters them by the supplied mod-presence check. */
    public static CompatRegistry load(Predicate<String> isModLoaded) {
        try (InputStream in = CompatRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("Bundled compat rules not found at {} — compat gating disabled.", RESOURCE);
                return empty();
            }
            return fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), isModLoaded);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read bundled compat rules — compat gating disabled.", e);
            return empty();
        }
    }

    /** Parses rules from JSON; malformed entries are skipped, never fatal. */
    public static CompatRegistry fromJson(Reader reader, Predicate<String> isModLoaded) {
        List<Rule> active = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) {
                LOGGER.warn("Compat rules file has no 'rules' array — compat gating disabled.");
                return empty();
            }
            for (JsonElement element : rules) {
                Rule rule = parseRule(element);
                if (rule != null && isModLoaded.test(rule.modId())) {
                    active.add(rule);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to parse compat rules — compat gating disabled.", e);
            return empty();
        }
        return new CompatRegistry(active);
    }

    public static CompatRegistry empty() {
        return new CompatRegistry(List.of());
    }

    private static Rule parseRule(JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            String modId = obj.get("mod").getAsString();
            String name = obj.has("name") ? obj.get("name").getAsString() : modId;
            Set<String> disables = new LinkedHashSet<>();
            if (obj.has("disables")) {
                for (JsonElement module : obj.getAsJsonArray("disables")) {
                    disables.add(module.getAsString());
                }
            }
            Map<String, Set<String>> restricts = new LinkedHashMap<>();
            if (obj.has("restricts")) {
                JsonObject restrictsObj = obj.getAsJsonObject("restricts");
                for (String module : restrictsObj.keySet()) {
                    Set<String> subFeatures = new LinkedHashSet<>();
                    for (JsonElement feature : restrictsObj.getAsJsonArray(module)) {
                        subFeatures.add(feature.getAsString());
                    }
                    restricts.put(module, Set.copyOf(subFeatures));
                }
            }
            return new Rule(modId, name, Set.copyOf(disables), Map.copyOf(restricts));
        } catch (RuntimeException e) {
            LOGGER.warn("Skipping malformed compat rule: {}", element, e);
            return null;
        }
    }

    /**
     * The id of the loaded mod that force-disables the given module, if any.
     * Checks the data rules; module-declared conflicts are merged in by
     * {@link #disabledBy(String, Collection, Predicate)}.
     */
    public Optional<String> disabledBy(String moduleId) {
        return activeRules.stream()
                .filter(rule -> rule.disables().contains(moduleId))
                .map(Rule::modId)
                .findFirst();
    }

    /** Merges the data rules with a module's own declared conflicts. */
    public Optional<String> disabledBy(String moduleId, Collection<String> declaredConflicts, Predicate<String> isModLoaded) {
        Optional<String> byRules = disabledBy(moduleId);
        if (byRules.isPresent()) {
            return byRules;
        }
        return declaredConflicts.stream().filter(isModLoaded).findFirst();
    }

    /** Sub-features of the module that must stay off because of loaded mods (union of all rules). */
    public Set<String> restrictedSubFeatures(String moduleId) {
        Set<String> out = new LinkedHashSet<>();
        for (Rule rule : activeRules) {
            out.addAll(rule.restricts().getOrDefault(moduleId, Set.of()));
        }
        return Set.copyOf(out);
    }

    /** Display name of a detected mod for log/GUI notes; falls back to the mod id. */
    public String displayName(String modId) {
        return activeRules.stream()
                .filter(rule -> rule.modId().equals(modId))
                .map(Rule::displayName)
                .findFirst()
                .orElse(modId);
    }

    /** Rules whose mod is actually loaded — the "detected mods" list for the status screen. */
    public List<Rule> activeRules() {
        return activeRules;
    }

    /**
     * One compat rule, kept only when its mod is loaded.
     *
     * @param modId the detected mod's id
     * @param displayName human-readable mod name for GUI/log notes
     * @param disables module ids that must be fully disabled
     * @param restricts module id → sub-feature ids that must stay off
     */
    public record Rule(String modId, String displayName, Set<String> disables, Map<String, Set<String>> restricts) {
    }
}
