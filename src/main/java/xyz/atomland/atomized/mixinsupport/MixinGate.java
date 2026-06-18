package xyz.atomland.atomized.mixinsupport;

import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.atomland.atomized.core.VersionGuard;

/**
 * The pure decision logic behind {@link AtomizedMixinPlugin}, factored out so it is
 * unit-testable without a Mixin environment.
 *
 * <p>Convention: a mixin class lives at
 * {@code <root>.<module_id>[.<sub>].SomeMixin}; its first sub-package below the root
 * names the owning module. Mixins under {@code <root>.core} (or directly in the root)
 * are infrastructure and always apply.
 *
 * <p>Decisions are conservative: a malformed version rule or a throwing predicate
 * means the mixin is NOT applied — a missing optimization is always safer than a
 * potentially broken one (fail-soft, master plan §4).
 */
public final class MixinGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/MixinGate");
    public static final String CORE_MODULE = "core";

    private final String rootPackage;
    private final Predicate<String> moduleEnabled;
    private final String minecraftVersion;
    private final Map<String, String> versionRules;

    /**
     * @param rootPackage the mixin package root, e.g. {@code "xyz.atomland.atomized.mixin"}
     * @param moduleEnabled config+compat decision per module id
     * @param minecraftVersion the running Minecraft version
     * @param versionRules mixin class-name prefix → version predicate for version-specific mixins
     */
    public MixinGate(String rootPackage, Predicate<String> moduleEnabled, String minecraftVersion,
                     Map<String, String> versionRules) {
        this.rootPackage = rootPackage.endsWith(".") ? rootPackage.substring(0, rootPackage.length() - 1) : rootPackage;
        this.moduleEnabled = moduleEnabled;
        this.minecraftVersion = minecraftVersion;
        this.versionRules = Map.copyOf(versionRules);
    }

    /** Extracts the owning module id, or {@link #CORE_MODULE} for root/infrastructure mixins. */
    public String moduleOf(String mixinClassName) {
        String prefix = rootPackage + ".";
        if (!mixinClassName.startsWith(prefix)) {
            return CORE_MODULE;
        }
        String relative = mixinClassName.substring(prefix.length());
        int dot = relative.indexOf('.');
        if (dot < 0) {
            return CORE_MODULE; // class directly in the root package
        }
        String first = relative.substring(0, dot);
        return first.equals("plugin") ? CORE_MODULE : first;
    }

    /** The gate: module enabled (config+compat) AND version rule (if any) satisfied. */
    public boolean shouldApply(String mixinClassName) {
        String module = moduleOf(mixinClassName);
        if (!CORE_MODULE.equals(module)) {
            boolean enabled;
            try {
                enabled = moduleEnabled.test(module);
            } catch (RuntimeException e) {
                LOGGER.warn("Module gate for '{}' failed — not applying {} (fail-soft).", module, mixinClassName, e);
                return false;
            }
            if (!enabled) {
                return false;
            }
        }
        for (Map.Entry<String, String> rule : versionRules.entrySet()) {
            if (mixinClassName.startsWith(rule.getKey())) {
                try {
                    if (!VersionGuard.matches(minecraftVersion, rule.getValue())) {
                        return false;
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Malformed version rule '{}' for {} — not applying (fail-soft).",
                            rule.getValue(), mixinClassName, e);
                    return false;
                }
            }
        }
        return true;
    }
}
