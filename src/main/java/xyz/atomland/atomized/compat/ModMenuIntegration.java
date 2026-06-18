package xyz.atomland.atomized.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import xyz.atomland.atomized.gui.AtomizedScreens;

/**
 * ModMenu entrypoint: wires the "Configure" button on Atomized's mod-list entry to the
 * settings screen.
 *
 * <p>Only referenced through the {@code modmenu} entrypoint, so the class — and its
 * compile-time reference to ModMenu's API — is loaded only when ModMenu is present.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AtomizedScreens::settings;
    }
}
