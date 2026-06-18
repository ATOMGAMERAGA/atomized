package xyz.atomland.atomized.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    private record StubModule(String id, boolean defaultEnabled) implements AtomizedModule {
        @Override
        public void init(ModuleContext context) {
        }

        @Override
        public Set<String> conflictsWith() {
            return Set.of();
        }
    }

    private static final List<StubModule> MODULES =
            List.of(new StubModule("alpha", true), new StubModule("beta", false));

    @TempDir
    Path dir;

    private ConfigManager manager() {
        return new ConfigManager(dir.resolve("atomized.json"), MODULES);
    }

    /** Forces a visibly different mtime regardless of filesystem timestamp granularity. */
    private static void touch(Path file, long epochSeconds) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(epochSeconds * 1000));
    }

    @Test
    void missingFileCreatesDefaultsAndWritesThem() {
        ConfigManager manager = manager();
        AtomizedConfig config = manager.load();

        assertTrue(config.isEnabled("alpha", true));
        assertFalse(config.isEnabled("beta", false));
        assertTrue(Files.exists(manager.file()));
    }

    @Test
    void currentBeforeLoadThrows() {
        assertThrows(IllegalStateException.class, () -> manager().current());
    }

    @Test
    void savedConfigSurvivesReload() {
        ConfigManager manager = manager();
        AtomizedConfig config = manager.load();
        config.setEnabled("alpha", false);
        config.set("alpha", "budget", 1234);
        manager.save(config);

        AtomizedConfig reloaded = manager().load();
        assertFalse(reloaded.isEnabled("alpha", true));
        assertEquals(1234, reloaded.getInt("alpha", "budget", -1));
    }

    @Test
    void corruptFileIsBackedUpAndReplacedByDefaults() throws IOException {
        Path file = dir.resolve("atomized.json");
        Files.writeString(file, "{ totally broken!!", StandardCharsets.UTF_8);

        AtomizedConfig config = manager().load();
        assertTrue(config.isEnabled("alpha", true));
        assertTrue(Files.exists(dir.resolve("atomized.json.corrupt")));
        // The corrupt content is preserved for the user.
        assertEquals("{ totally broken!!", Files.readString(dir.resolve("atomized.json.corrupt")));
        // And the main file is now valid.
        AtomizedConfig reloaded = manager().load();
        assertTrue(reloaded.isEnabled("alpha", true));
    }

    @Test
    void v0FileIsMigratedOnLoad() throws IOException {
        Path file = dir.resolve("atomized.json");
        Files.writeString(file, "{ \"modules\": { \"alpha\": false } }", StandardCharsets.UTF_8);

        AtomizedConfig config = manager().load();
        assertFalse(config.isEnabled("alpha", true));
        // load() rewrites the migrated form including the schema version.
        assertTrue(Files.readString(file).contains("\"config_version\""));
    }

    @Test
    void reloadIfChangedIsNoOpWhenFileUntouched() {
        ConfigManager manager = manager();
        manager.load();
        assertFalse(manager.reloadIfChanged());
    }

    @Test
    void reloadIfChangedPicksUpExternalEdit() throws IOException {
        ConfigManager manager = manager();
        manager.load();

        Path file = manager.file();
        String text = Files.readString(file).replace("\"enabled\": true", "\"enabled\": false");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        touch(file, 99_999_999L);

        assertTrue(manager.reloadIfChanged());
        assertFalse(manager.current().isEnabled("alpha", true));
    }

    @Test
    void reloadNotifiesListeners() throws IOException {
        ConfigManager manager = manager();
        manager.load();
        AtomicReference<AtomizedConfig> seen = new AtomicReference<>();
        manager.addListener(seen::set);

        touch(manager.file(), 88_888_888L);
        assertTrue(manager.reloadIfChanged());
        assertEquals(manager.current(), seen.get());
    }

    @Test
    void throwingListenerDoesNotBreakReload() throws IOException {
        ConfigManager manager = manager();
        manager.load();
        AtomicInteger calls = new AtomicInteger();
        manager.addListener(cfg -> {
            throw new RuntimeException("listener bug");
        });
        manager.addListener(cfg -> calls.incrementAndGet());

        touch(manager.file(), 77_777_777L);
        assertTrue(manager.reloadIfChanged());
        assertEquals(1, calls.get());
    }

    @Test
    void forceReloadAlwaysRereads() throws IOException {
        ConfigManager manager = manager();
        manager.load();
        String text = Files.readString(manager.file()).replace("\"enabled\": true", "\"enabled\": false");
        Files.writeString(manager.file(), text, StandardCharsets.UTF_8);
        // No mtime poke: forceReload must not depend on change detection.
        assertTrue(manager.forceReload());
        assertFalse(manager.current().isEnabled("alpha", true));
    }

    @Test
    void corruptionAppearingAtReloadFallsBackToDefaults() throws IOException {
        ConfigManager manager = manager();
        AtomizedConfig config = manager.load();
        config.setEnabled("alpha", false);
        manager.save(config);

        Files.writeString(manager.file(), "epic corruption", StandardCharsets.UTF_8);
        touch(manager.file(), 66_666_666L);

        assertTrue(manager.reloadIfChanged());
        // Defaults again — and no crash.
        assertTrue(manager.current().isEnabled("alpha", true));
        assertTrue(Files.exists(dir.resolve("atomized.json.corrupt")));
    }

    @Test
    void reloadIfChangedBeforeLoadIsSafeNoOp() {
        assertFalse(manager().reloadIfChanged());
    }

    @Test
    void unknownModulesInFileSurviveLoadAndSave() throws IOException {
        Path file = dir.resolve("atomized.json");
        Files.writeString(file, """
                { "config_version": 1, "modules": { "from_the_future": { "enabled": false } } }
                """, StandardCharsets.UTF_8);

        ConfigManager manager = manager();
        AtomizedConfig config = manager.load();
        assertFalse(config.isEnabled("from_the_future", true));
        // load() rewrites the file; the unknown module must still be there.
        assertTrue(Files.readString(file).contains("from_the_future"));
    }
}
