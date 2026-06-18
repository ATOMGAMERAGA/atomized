package xyz.atomland.atomized.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.atomland.atomized.core.AtomizedModule;

/**
 * Loads, saves and hot-reloads {@code config/atomized.json}.
 *
 * <p>Fail-soft guarantees:
 * <ul>
 *   <li>Missing file → defaults are created and written.</li>
 *   <li>Corrupt file → it is preserved as {@code atomized.json.corrupt} for the user,
 *       defaults take over, the game never crashes.</li>
 *   <li>Old schema → migrated via {@link ConfigMigrations} and rewritten.</li>
 * </ul>
 *
 * <p>Hot-reload is polling-based ({@link #reloadIfChanged()} is called from a slow
 * client-tick cadence); listeners are notified with the fresh config.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Collection<? extends AtomizedModule> modules;
    private final List<Consumer<AtomizedConfig>> listeners = new CopyOnWriteArrayList<>();

    private volatile AtomizedConfig current;
    private volatile FileTime lastSeenModification;

    public ConfigManager(Path file, Collection<? extends AtomizedModule> modules) {
        this.file = file;
        this.modules = modules;
    }

    public Path file() {
        return file;
    }

    /** The most recently loaded config; {@link #load()} must have been called first. */
    public AtomizedConfig current() {
        AtomizedConfig config = current;
        if (config == null) {
            throw new IllegalStateException("ConfigManager.load() has not been called yet");
        }
        return config;
    }

    /** Loads the config according to the fail-soft rules above. Never throws. */
    public synchronized AtomizedConfig load() {
        AtomizedConfig loaded;
        if (!Files.exists(file)) {
            loaded = AtomizedConfig.defaults(modules);
            save(loaded);
        } else {
            loaded = readOrRecover();
            save(loaded); // normalize formatting + write migrated/repaired form
        }
        current = loaded;
        rememberModificationTime();
        return loaded;
    }

    /** Saves the given config and adopts it as current. Never throws. */
    public synchronized void save(AtomizedConfig config) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(config.toJson()), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not write config file {} — settings will not persist this session.", file, e);
        }
        current = config;
        rememberModificationTime();
    }

    /**
     * Re-reads the file if it changed on disk since the last load/save and notifies
     * listeners. Returns whether a reload happened.
     */
    public synchronized boolean reloadIfChanged() {
        if (current == null) {
            return false;
        }
        try {
            if (!Files.exists(file)) {
                return false;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(lastSeenModification)) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return forceReload();
    }

    /** Unconditionally re-reads the file (the {@code /atomized reload} path). */
    public synchronized boolean forceReload() {
        AtomizedConfig loaded = readOrRecover();
        current = loaded;
        rememberModificationTime();
        for (Consumer<AtomizedConfig> listener : listeners) {
            try {
                listener.accept(loaded);
            } catch (RuntimeException e) {
                LOGGER.warn("Config reload listener failed.", e);
            }
        }
        return true;
    }

    public void addListener(Consumer<AtomizedConfig> listener) {
        listeners.add(listener);
    }

    private AtomizedConfig readOrRecover() {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            ConfigMigrations.migrate(root);
            return AtomizedConfig.fromJson(root, modules);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Config file {} is unreadable or corrupt; backing it up and using defaults.", file, e);
            backupCorruptFile();
            return AtomizedConfig.defaults(modules);
        }
    }

    private void backupCorruptFile() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupt config preserved at {}.", backup);
        } catch (IOException e) {
            LOGGER.warn("Could not back up corrupt config file.", e);
        }
    }

    private void rememberModificationTime() {
        try {
            lastSeenModification = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            lastSeenModification = null;
        }
    }
}
