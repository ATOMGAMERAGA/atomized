package xyz.atomland.atomized.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.atomland.atomized.config.AtomizedConfig;

/**
 * Owns the module lifecycle: register → gate → init → tick (master plan §4).
 *
 * <p>Gate order for each module: user config → compat rules (data + module-declared
 * conflicts) → Minecraft version range. Initialization is fail-soft: a throwing module
 * is reported to the {@link PanicSwitch} and marked {@code FAILED}; the game never
 * crashes because of a module.
 */
public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/ModuleManager");

    private final PanicSwitch panicSwitch;
    private final Predicate<String> isModLoaded;
    private final String minecraftVersion;
    private final Map<String, AtomizedModule> modules = new LinkedHashMap<>();
    private final Map<String, ModuleStatus> statuses = new LinkedHashMap<>();
    private boolean initialized;

    public ModuleManager(PanicSwitch panicSwitch, Predicate<String> isModLoaded, String minecraftVersion) {
        this.panicSwitch = panicSwitch;
        this.isModLoaded = isModLoaded;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Registers a module. Must happen before {@link #initAll}.
     *
     * @throws IllegalArgumentException on duplicate module ids
     * @throws IllegalStateException when called after initialization
     */
    public synchronized void register(AtomizedModule module) {
        if (initialized) {
            throw new IllegalStateException("Cannot register module '" + module.id() + "' after initAll()");
        }
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
    }

    /**
     * Gates and initializes every registered module, in registration order.
     * Subsequent calls are ignored with a warning (double-init protection).
     */
    public synchronized void initAll(AtomizedConfig config, CompatRegistry compat) {
        if (initialized) {
            LOGGER.warn("initAll() called twice — ignoring the second call.");
            return;
        }
        initialized = true;
        ModuleContext context = new ModuleContext(config, compat);
        for (AtomizedModule module : modules.values()) {
            statuses.put(module.id(), gateAndInit(module, config, compat, context));
        }
        long enabled = statuses.values().stream().filter(ModuleStatus::isEnabled).count();
        LOGGER.info("Modules initialized: {} enabled, {} gated/failed.", enabled, statuses.size() - enabled);
    }

    private ModuleStatus gateAndInit(AtomizedModule module, AtomizedConfig config, CompatRegistry compat,
                                     ModuleContext context) {
        String id = module.id();
        if (!config.isEnabled(id, module.defaultEnabled())) {
            return new ModuleStatus(id, ModuleStatus.ModuleState.DISABLED_CONFIG, "");
        }
        Optional<String> conflict = compat.disabledBy(id, module.conflictsWith(), isModLoaded);
        if (conflict.isPresent()) {
            String modId = conflict.get();
            LOGGER.info("Module '{}' disabled: overlapping mod '{}' detected.", id, compat.displayName(modId));
            return new ModuleStatus(id, ModuleStatus.ModuleState.DISABLED_CONFLICT, modId);
        }
        String range = module.minecraftRange();
        boolean versionOk;
        try {
            versionOk = VersionGuard.matches(minecraftVersion, range);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Module '{}' declares malformed version range '{}' — disabling it.", id, range, e);
            versionOk = false;
        }
        if (!versionOk) {
            return new ModuleStatus(id, ModuleStatus.ModuleState.DISABLED_VERSION, range);
        }
        try {
            module.init(context);
            return ModuleStatus.enabled(id);
        } catch (Throwable t) {
            panicSwitch.report(id, "init", t);
            return new ModuleStatus(id, ModuleStatus.ModuleState.FAILED, summarize(t));
        }
    }

    /** Ticks enabled modules; a throwing module is shut down fail-soft. */
    public synchronized void tickAll() {
        if (!initialized) {
            return;
        }
        for (AtomizedModule module : modules.values()) {
            if (!statuses.get(module.id()).isEnabled()) {
                continue;
            }
            try {
                module.tick();
            } catch (Throwable t) {
                panicSwitch.report(module.id(), "tick", t);
                statuses.put(module.id(), new ModuleStatus(module.id(), ModuleStatus.ModuleState.FAILED, summarize(t)));
            }
        }
    }

    /** Propagates a config change to enabled modules; a throwing module is shut down fail-soft. */
    public synchronized void onConfigChange(AtomizedConfig config) {
        if (!initialized) {
            return;
        }
        for (AtomizedModule module : modules.values()) {
            if (!statuses.get(module.id()).isEnabled()) {
                continue;
            }
            try {
                module.onConfigChange(config);
            } catch (Throwable t) {
                panicSwitch.report(module.id(), "config", t);
                statuses.put(module.id(), new ModuleStatus(module.id(), ModuleStatus.ModuleState.FAILED, summarize(t)));
            }
        }
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }

    public synchronized Optional<ModuleStatus> status(String moduleId) {
        return Optional.ofNullable(statuses.get(moduleId));
    }

    /** Whether the module passed all gates and is currently running. */
    public synchronized boolean isModuleEnabled(String moduleId) {
        ModuleStatus status = statuses.get(moduleId);
        return status != null && status.isEnabled();
    }

    public synchronized List<ModuleStatus> statuses() {
        return List.copyOf(statuses.values());
    }

    public synchronized List<AtomizedModule> registeredModules() {
        return List.copyOf(modules.values());
    }

    private static String summarize(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
