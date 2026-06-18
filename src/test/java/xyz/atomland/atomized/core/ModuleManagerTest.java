package xyz.atomland.atomized.core;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import xyz.atomland.atomized.config.AtomizedConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerTest {

    /** Configurable fake module. */
    private static class FakeModule implements AtomizedModule {
        final String id;
        boolean defaultEnabled = true;
        Set<String> conflicts = Set.of();
        String range = "*";
        RuntimeException initFailure;
        RuntimeException tickFailure;
        RuntimeException configFailure;
        final AtomicInteger initCount = new AtomicInteger();
        final AtomicInteger tickCount = new AtomicInteger();
        final AtomicInteger configCount = new AtomicInteger();

        FakeModule(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean defaultEnabled() {
            return defaultEnabled;
        }

        @Override
        public Set<String> conflictsWith() {
            return conflicts;
        }

        @Override
        public String minecraftRange() {
            return range;
        }

        @Override
        public void init(ModuleContext context) {
            initCount.incrementAndGet();
            if (initFailure != null) {
                throw initFailure;
            }
        }

        @Override
        public void tick() {
            tickCount.incrementAndGet();
            if (tickFailure != null) {
                throw tickFailure;
            }
        }

        @Override
        public void onConfigChange(AtomizedConfig config) {
            configCount.incrementAndGet();
            if (configFailure != null) {
                throw configFailure;
            }
        }
    }

    private static ModuleManager manager(PanicSwitch panic, String mcVersion, String... loadedMods) {
        return new ModuleManager(panic, Set.of(loadedMods)::contains, mcVersion);
    }

    private static AtomizedConfig configFor(FakeModule... modules) {
        return AtomizedConfig.defaults(List.of(modules));
    }

    @Test
    void enabledModuleIsInitialized() {
        FakeModule module = new FakeModule("frame_pacing");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());

        assertEquals(1, module.initCount.get());
        assertTrue(manager.isModuleEnabled("frame_pacing"));
        assertEquals(ModuleStatus.ModuleState.ENABLED, manager.status("frame_pacing").orElseThrow().state());
    }

    @Test
    void duplicateRegistrationThrows() {
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(new FakeModule("a"));
        assertThrows(IllegalArgumentException.class, () -> manager.register(new FakeModule("a")));
    }

    @Test
    void registrationAfterInitThrows() {
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.initAll(AtomizedConfig.defaults(List.of()), CompatRegistry.empty());
        assertThrows(IllegalStateException.class, () -> manager.register(new FakeModule("late")));
    }

    @Test
    void doubleInitIsIgnored() {
        FakeModule module = new FakeModule("a");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        AtomizedConfig config = configFor(module);
        manager.initAll(config, CompatRegistry.empty());
        manager.initAll(config, CompatRegistry.empty());
        assertEquals(1, module.initCount.get());
    }

    @Test
    void configDisabledModuleIsNotInitialized() {
        FakeModule module = new FakeModule("gui_opt");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        AtomizedConfig config = configFor(module);
        config.setEnabled("gui_opt", false);
        manager.initAll(config, CompatRegistry.empty());

        assertEquals(0, module.initCount.get());
        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFIG, manager.status("gui_opt").orElseThrow().state());
    }

    @Test
    void defaultDisabledModuleStaysOffWhenAbsentFromConfig() {
        FakeModule module = new FakeModule("aggressive_thing");
        module.defaultEnabled = false;
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        // Config knows nothing about this module (e.g. file from an older version).
        manager.initAll(AtomizedConfig.defaults(List.of()), CompatRegistry.empty());

        assertEquals(0, module.initCount.get());
        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFIG, manager.status("aggressive_thing").orElseThrow().state());
    }

    @Test
    void compatRuleDisablesModule() {
        FakeModule module = new FakeModule("smart_culling");
        CompatRegistry compat = CompatRegistry.fromJson(new StringReader("""
                { "rules": [ { "mod": "entityculling", "name": "EntityCulling", "disables": ["smart_culling"] } ] }
                """), Set.of("entityculling")::contains);
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11", "entityculling");
        manager.register(module);
        manager.initAll(configFor(module), compat);

        assertEquals(0, module.initCount.get());
        ModuleStatus status = manager.status("smart_culling").orElseThrow();
        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFLICT, status.state());
        assertEquals("entityculling", status.detail());
    }

    @Test
    void moduleDeclaredConflictDisablesModule() {
        FakeModule module = new FakeModule("idle_throttle");
        module.conflicts = Set.of("dynamic_fps");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11", "dynamic_fps");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());

        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFLICT, manager.status("idle_throttle").orElseThrow().state());
        assertEquals("dynamic_fps", manager.status("idle_throttle").orElseThrow().detail());
    }

    @Test
    void versionGateDisablesOutOfRangeModule() {
        FakeModule module = new FakeModule("new_thing");
        module.range = ">=1.21.5";
        ModuleManager manager = manager(new PanicSwitch(), "1.21.1");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());

        ModuleStatus status = manager.status("new_thing").orElseThrow();
        assertEquals(ModuleStatus.ModuleState.DISABLED_VERSION, status.state());
        assertEquals(">=1.21.5", status.detail());
    }

    @Test
    void versionGateAllowsInRangeModule() {
        FakeModule module = new FakeModule("new_thing");
        module.range = ">=1.21.5";
        ModuleManager manager = manager(new PanicSwitch(), "1.21.8");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());
        assertTrue(manager.isModuleEnabled("new_thing"));
    }

    @Test
    void malformedModuleRangeDisablesModuleInsteadOfCrashing() {
        FakeModule module = new FakeModule("weird");
        module.range = ">=banana";
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());
        assertEquals(ModuleStatus.ModuleState.DISABLED_VERSION, manager.status("weird").orElseThrow().state());
    }

    @Test
    void initFailureIsFailSoft() {
        FakeModule failing = new FakeModule("explodes");
        failing.initFailure = new IllegalStateException("kaboom");
        FakeModule healthy = new FakeModule("healthy");
        PanicSwitch panic = new PanicSwitch();
        ModuleManager manager = manager(panic, "1.21.11");
        manager.register(failing);
        manager.register(healthy);
        manager.initAll(configFor(failing, healthy), CompatRegistry.empty());

        assertEquals(ModuleStatus.ModuleState.FAILED, manager.status("explodes").orElseThrow().state());
        assertTrue(panic.hasIncident("explodes"));
        // The failure of one module never prevents the next from initializing.
        assertTrue(manager.isModuleEnabled("healthy"));
    }

    @Test
    void tickReachesOnlyEnabledModules() {
        FakeModule enabled = new FakeModule("on");
        FakeModule disabled = new FakeModule("off");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(enabled);
        manager.register(disabled);
        AtomizedConfig config = configFor(enabled, disabled);
        config.setEnabled("off", false);
        manager.initAll(config, CompatRegistry.empty());

        manager.tickAll();
        manager.tickAll();
        assertEquals(2, enabled.tickCount.get());
        assertEquals(0, disabled.tickCount.get());
    }

    @Test
    void tickFailureShutsModuleDownFailSoft() {
        FakeModule failing = new FakeModule("ticker");
        failing.tickFailure = new RuntimeException("tick boom");
        PanicSwitch panic = new PanicSwitch();
        ModuleManager manager = manager(panic, "1.21.11");
        manager.register(failing);
        manager.initAll(configFor(failing), CompatRegistry.empty());

        manager.tickAll();
        assertEquals(1, failing.tickCount.get());
        assertEquals(ModuleStatus.ModuleState.FAILED, manager.status("ticker").orElseThrow().state());
        assertTrue(panic.hasIncident("ticker"));

        manager.tickAll(); // module is now off; tick must not reach it again
        assertEquals(1, failing.tickCount.get());
    }

    @Test
    void tickBeforeInitIsANoOp() {
        FakeModule module = new FakeModule("a");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(module);
        manager.tickAll();
        assertEquals(0, module.tickCount.get());
    }

    @Test
    void configChangeReachesOnlyEnabledModules() {
        FakeModule enabled = new FakeModule("on");
        FakeModule disabled = new FakeModule("off");
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.register(enabled);
        manager.register(disabled);
        AtomizedConfig config = configFor(enabled, disabled);
        config.setEnabled("off", false);
        manager.initAll(config, CompatRegistry.empty());

        manager.onConfigChange(config);
        assertEquals(1, enabled.configCount.get());
        assertEquals(0, disabled.configCount.get());
    }

    @Test
    void configChangeFailureShutsModuleDownFailSoft() {
        FakeModule failing = new FakeModule("cfg");
        failing.configFailure = new RuntimeException("cfg boom");
        PanicSwitch panic = new PanicSwitch();
        ModuleManager manager = manager(panic, "1.21.11");
        manager.register(failing);
        AtomizedConfig config = configFor(failing);
        manager.initAll(config, CompatRegistry.empty());

        manager.onConfigChange(config);
        assertEquals(ModuleStatus.ModuleState.FAILED, manager.status("cfg").orElseThrow().state());
        assertTrue(panic.hasIncident("cfg"));
    }

    @Test
    void statusesPreserveRegistrationOrder() {
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        FakeModule a = new FakeModule("a");
        FakeModule b = new FakeModule("b");
        FakeModule c = new FakeModule("c");
        manager.register(a);
        manager.register(b);
        manager.register(c);
        manager.initAll(configFor(a, b, c), CompatRegistry.empty());

        assertEquals(List.of("a", "b", "c"), manager.statuses().stream().map(ModuleStatus::moduleId).toList());
    }

    @Test
    void statusOfUnknownModuleIsEmpty() {
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11");
        manager.initAll(AtomizedConfig.defaults(List.of()), CompatRegistry.empty());
        assertTrue(manager.status("nope").isEmpty());
        assertFalse(manager.isModuleEnabled("nope"));
    }

    @Test
    void gateOrderIsConfigThenConflictThenVersion() {
        // A module failing every gate must be reported as DISABLED_CONFIG (first gate).
        FakeModule module = new FakeModule("multi");
        module.conflicts = Set.of("somemod");
        module.range = "=9.9.9";
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11", "somemod");
        manager.register(module);
        AtomizedConfig config = configFor(module);
        config.setEnabled("multi", false);
        manager.initAll(config, CompatRegistry.empty());
        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFIG, manager.status("multi").orElseThrow().state());
    }

    @Test
    void conflictGateBeatsVersionGate() {
        FakeModule module = new FakeModule("multi");
        module.conflicts = Set.of("somemod");
        module.range = "=9.9.9";
        ModuleManager manager = manager(new PanicSwitch(), "1.21.11", "somemod");
        manager.register(module);
        manager.initAll(configFor(module), CompatRegistry.empty());
        assertEquals(ModuleStatus.ModuleState.DISABLED_CONFLICT, manager.status("multi").orElseThrow().state());
    }
}
