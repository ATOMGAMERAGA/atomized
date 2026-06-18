package xyz.atomland.atomized.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.config.ConfigManager;
import xyz.atomland.atomized.core.ModuleManager;
import xyz.atomland.atomized.core.ModuleStatus;

/**
 * The Sodium-free fallback settings screen: one toggle row per module plus Done/Cancel.
 *
 * <p>Built only from version-stable client API (Screen, Button, GuiGraphics) verified
 * across 1.21.1–1.21.11, so a single implementation serves every target. Read-only rows
 * (conflict/version/failed) show their reason instead of a toggle. The toggle/commit
 * logic lives in {@link ConfigScreenModel} and is unit-tested without a client.
 */
public final class AtomizedConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 36;
    private static final int BUTTON_WIDTH = 220;
    private static final int TOGGLE_WIDTH = 70;

    private final Screen parent;
    private final ConfigManager configManager;
    private final ModuleManager moduleManager;
    private final ConfigScreenModel model;

    public AtomizedConfigScreen(Screen parent, ConfigManager configManager, ModuleManager moduleManager) {
        super(Component.translatable("atomized.screen.title"));
        this.parent = parent;
        this.configManager = configManager;
        this.moduleManager = moduleManager;
        this.model = new ConfigScreenModel(moduleManager.statuses(), configManager.current());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = LIST_TOP;
        for (ConfigScreenModel.Row row : model.rows()) {
            addModuleRow(centerX, y, row);
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onDone())
                .bounds(centerX - 154, this.height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX + 4, this.height - 28, 150, 20).build());
    }

    private void addModuleRow(int centerX, int y, ConfigScreenModel.Row row) {
        int left = centerX - BUTTON_WIDTH / 2;
        Component label = moduleLabel(row);
        if (row.toggleable()) {
            Button toggle = Button.builder(toggleLabel(model.isEnabled(row.moduleId())), button -> {
                boolean now = model.toggle(row.moduleId());
                button.setMessage(toggleLabel(now));
            }).bounds(left, y, TOGGLE_WIDTH, 20).build();
            addRenderableWidget(toggle);
            Button name = Button.builder(label, b -> {
            }).bounds(left + TOGGLE_WIDTH + 4, y, BUTTON_WIDTH - TOGGLE_WIDTH - 4, 20).build();
            name.active = false;
            addRenderableWidget(name);
        } else {
            Button name = Button.builder(label, b -> {
            }).bounds(left, y, BUTTON_WIDTH, 20).build();
            name.active = false;
            addRenderableWidget(name);
        }
    }

    private static Component toggleLabel(boolean enabled) {
        return enabled
                ? Component.translatable("atomized.screen.on")
                : Component.translatable("atomized.screen.off");
    }

    private Component moduleLabel(ConfigScreenModel.Row row) {
        String key = "atomized.module." + row.moduleId();
        Component name = Component.translatable(key);
        return switch (row.gateState()) {
            case DISABLED_CONFLICT -> Component.translatable("atomized.screen.row.conflict", name, row.detail());
            case DISABLED_VERSION -> Component.translatable("atomized.screen.row.version", name);
            case FAILED -> Component.translatable("atomized.screen.row.failed", name);
            default -> name;
        };
    }

    private void onDone() {
        AtomizedConfig config = configManager.current();
        if (model.commit(config)) {
            configManager.save(config);
            moduleManager.onConfigChange(config);
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        if (model.rows().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("atomized.screen.empty"),
                    this.width / 2, LIST_TOP + 8, 0xAAAAAA);
        }
    }

    /** Exposed for diagnostics/tests that need to know how many module rows were built. */
    public int rowCount() {
        return model.rows().size();
    }

    /** The live gate statuses backing this screen. */
    public java.util.List<ModuleStatus> statuses() {
        return moduleManager.statuses();
    }
}
