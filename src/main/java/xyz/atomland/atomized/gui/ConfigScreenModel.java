package xyz.atomland.atomized.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.core.ModuleStatus;

/**
 * The Minecraft-free logic behind {@link AtomizedConfigScreen}: which modules to show,
 * which are user-toggleable, the pending on/off state, dirty tracking and committing
 * back to the config. Kept free of client classes so it is fully unit-testable.
 *
 * <p>A module is user-toggleable only when its gate state is {@link ModuleStatus.ModuleState#ENABLED}
 * or {@link ModuleStatus.ModuleState#DISABLED_CONFIG}; conflict/version/failed states are
 * shown read-only (the user cannot turn on a module a conflicting mod has disabled).
 */
public final class ConfigScreenModel {

    /** One row in the screen. */
    public record Row(String moduleId, ModuleStatus.ModuleState gateState, String detail, boolean toggleable) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Boolean> pending = new LinkedHashMap<>();
    private final Map<String, Boolean> original = new LinkedHashMap<>();

    /**
     * @param statuses the live gate statuses (registration order preserved)
     * @param config the active config, read for the current enabled flags
     */
    public ConfigScreenModel(List<ModuleStatus> statuses, AtomizedConfig config) {
        for (ModuleStatus status : statuses) {
            boolean toggleable = status.state() == ModuleStatus.ModuleState.ENABLED
                    || status.state() == ModuleStatus.ModuleState.DISABLED_CONFIG;
            rows.add(new Row(status.moduleId(), status.state(), status.detail(), toggleable));
            boolean enabled = config.isEnabled(status.moduleId());
            original.put(status.moduleId(), enabled);
            pending.put(status.moduleId(), enabled);
        }
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public boolean isToggleable(String moduleId) {
        return rows.stream().anyMatch(r -> r.moduleId().equals(moduleId) && r.toggleable());
    }

    /** The pending (possibly uncommitted) enabled state for a module. */
    public boolean isEnabled(String moduleId) {
        return pending.getOrDefault(moduleId, false);
    }

    /**
     * Flips the pending state of a toggleable module. No-op for read-only rows.
     *
     * @return the new pending state
     */
    public boolean toggle(String moduleId) {
        if (!isToggleable(moduleId)) {
            return isEnabled(moduleId);
        }
        boolean next = !isEnabled(moduleId);
        pending.put(moduleId, next);
        return next;
    }

    public void setEnabled(String moduleId, boolean enabled) {
        if (isToggleable(moduleId)) {
            pending.put(moduleId, enabled);
        }
    }

    /** Whether any pending state differs from what was loaded. */
    public boolean isDirty() {
        return !pending.equals(original);
    }

    /** Module ids whose pending state differs from the original. */
    public List<String> changedModules() {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : pending.entrySet()) {
            if (!entry.getValue().equals(original.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /**
     * Writes pending states into the config. Returns whether anything changed (the caller
     * persists + reloads only when {@code true}). After commit the model is clean.
     */
    public boolean commit(AtomizedConfig config) {
        boolean changed = isDirty();
        for (Map.Entry<String, Boolean> entry : pending.entrySet()) {
            config.setEnabled(entry.getKey(), entry.getValue());
        }
        original.clear();
        original.putAll(pending);
        return changed;
    }
}
