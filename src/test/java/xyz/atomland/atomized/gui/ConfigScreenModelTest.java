package xyz.atomland.atomized.gui;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.AtomizedModule;
import xyz.atomland.atomized.core.ModuleContext;
import xyz.atomland.atomized.core.ModuleStatus;
import xyz.atomland.atomized.core.ModuleStatus.ModuleState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigScreenModelTest {

    private record Stub(String id) implements AtomizedModule {
        @Override
        public void init(ModuleContext context) {
        }

        @Override
        public Set<String> conflictsWith() {
            return Set.of();
        }
    }

    private static AtomizedConfig config(String... moduleIds) {
        return AtomizedConfig.defaults(List.of(moduleIds).stream().map(Stub::new).toList());
    }

    private static ModuleStatus status(String id, ModuleState state) {
        return new ModuleStatus(id, state, "");
    }

    @Test
    void rowsMirrorStatusOrder() {
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.DISABLED_CONFIG),
                status("c", ModuleState.DISABLED_CONFLICT)), config("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), model.rows().stream().map(ConfigScreenModel.Row::moduleId).toList());
    }

    @Test
    void enabledAndConfigDisabledAreToggleable() {
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.DISABLED_CONFIG)), config("a", "b"));
        assertTrue(model.isToggleable("a"));
        assertTrue(model.isToggleable("b"));
    }

    @Test
    void conflictVersionAndFailedAreReadOnly() {
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.DISABLED_CONFLICT),
                status("b", ModuleState.DISABLED_VERSION),
                status("c", ModuleState.FAILED)), config("a", "b", "c"));
        assertFalse(model.isToggleable("a"));
        assertFalse(model.isToggleable("b"));
        assertFalse(model.isToggleable("c"));
    }

    @Test
    void pendingStateReflectsConfig() {
        AtomizedConfig config = config("a", "b");
        config.setEnabled("a", true);
        config.setEnabled("b", false);
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.DISABLED_CONFIG)), config);
        assertTrue(model.isEnabled("a"));
        assertFalse(model.isEnabled("b"));
    }

    @Test
    void toggleFlipsToggleableModule() {
        var model = new ConfigScreenModel(List.of(status("a", ModuleState.ENABLED)), config("a"));
        assertTrue(model.isEnabled("a"));
        assertFalse(model.toggle("a"));
        assertFalse(model.isEnabled("a"));
        assertTrue(model.toggle("a"));
    }

    @Test
    void toggleIsNoOpForReadOnlyModule() {
        var model = new ConfigScreenModel(List.of(status("a", ModuleState.DISABLED_CONFLICT)), config("a"));
        boolean before = model.isEnabled("a");
        assertEquals(before, model.toggle("a"));
        assertEquals(before, model.isEnabled("a"));
    }

    @Test
    void dirtyTrackingAndChangedModules() {
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.ENABLED)), config("a", "b"));
        assertFalse(model.isDirty());
        model.toggle("a");
        assertTrue(model.isDirty());
        assertEquals(List.of("a"), model.changedModules());
    }

    @Test
    void togglingBackToOriginalIsNotDirty() {
        var model = new ConfigScreenModel(List.of(status("a", ModuleState.ENABLED)), config("a"));
        model.toggle("a");
        model.toggle("a");
        assertFalse(model.isDirty());
        assertTrue(model.changedModules().isEmpty());
    }

    @Test
    void commitWritesPendingAndReturnsChanged() {
        AtomizedConfig config = config("a", "b");
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.ENABLED)), config);
        model.toggle("a");
        assertTrue(model.commit(config));
        assertFalse(config.isEnabled("a"));
        assertTrue(config.isEnabled("b"));
        // Clean after commit.
        assertFalse(model.isDirty());
    }

    @Test
    void commitWithoutChangesReturnsFalse() {
        AtomizedConfig config = config("a");
        var model = new ConfigScreenModel(List.of(status("a", ModuleState.ENABLED)), config);
        assertFalse(model.commit(config));
    }

    @Test
    void setEnabledRespectsToggleability() {
        var model = new ConfigScreenModel(List.of(
                status("a", ModuleState.ENABLED),
                status("b", ModuleState.DISABLED_CONFLICT)), config("a", "b"));
        model.setEnabled("a", false);
        assertFalse(model.isEnabled("a"));
        boolean lockedBefore = model.isEnabled("b");
        model.setEnabled("b", !lockedBefore);
        assertEquals(lockedBefore, model.isEnabled("b"));
    }

    @Test
    void detailIsCarriedThrough() {
        var model = new ConfigScreenModel(
                List.of(new ModuleStatus("a", ModuleState.DISABLED_CONFLICT, "entityculling")), config("a"));
        assertEquals("entityculling", model.rows().get(0).detail());
    }

    @Test
    void emptyModelIsClean() {
        var model = new ConfigScreenModel(List.of(), config());
        assertFalse(model.isDirty());
        assertTrue(model.rows().isEmpty());
    }
}
