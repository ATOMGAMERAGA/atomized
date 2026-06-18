package xyz.atomland.atomized.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Registers the "Atomized Settings" keybind (unbound by default) and opens the settings
 * screen when it is pressed.
 *
 * <p>The {@link KeyMapping} constructor's category argument changed in 1.21.10 (from a
 * translation-key {@code String} to a {@code KeyMapping.Category} object), so that single
 * line is resolved per target with Stonecutter conditional compilation.
 */
public final class AtomizedKeybinds {
    private static final String TRANSLATION_KEY = "key.atomized.settings";
    /** GLFW "unknown" key — the bind ships unassigned (master plan §9). */
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    private static KeyMapping openSettings;

    private AtomizedKeybinds() {
    }

    public static void register() {
        openSettings = KeyBindingHelper.registerKeyBinding(createMapping());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(AtomizedScreens.settings(null));
                }
            }
        });
    }

    private static KeyMapping createMapping() {
        //? if >=1.21.10 {
        return new KeyMapping(TRANSLATION_KEY, InputConstants.Type.KEYSYM, UNBOUND, KeyMapping.Category.MISC);
        //?} else
        /*return new KeyMapping(TRANSLATION_KEY, InputConstants.Type.KEYSYM, UNBOUND, "key.categories.misc");*/
    }
}
