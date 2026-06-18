package xyz.atomland.atomized.gui;

import net.minecraft.client.gui.screens.Screen;
import xyz.atomland.atomized.Atomized;
import xyz.atomland.atomized.AtomizedBootstrap;
import xyz.atomland.atomized.core.ModuleManager;

/**
 * Single entry point for building Atomized's settings screen.
 *
 * <p>Today this always returns the version-stable {@link AtomizedConfigScreen}. When the
 * Sodium options integration lands it will return a screen that opens Sodium's own video
 * settings with the Atomized page selected (and still fall back here when Sodium is absent).
 */
public final class AtomizedScreens {
    private AtomizedScreens() {
    }

    /** Builds the settings screen to return to {@code parent} when closed. */
    public static Screen settings(Screen parent) {
        ModuleManager manager = Atomized.moduleManager();
        if (manager == null) {
            // Client entrypoint has not run yet — should not happen from a live screen,
            // but never hand back null.
            manager = new ModuleManager(AtomizedBootstrap.get().panicSwitch(), id -> false,
                    AtomizedBootstrap.get().minecraftVersion());
        }
        return new AtomizedConfigScreen(parent, AtomizedBootstrap.get().configManager(), manager);
    }
}
