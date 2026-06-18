package xyz.atomland.atomized;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import xyz.atomland.atomized.config.AtomizedConfig;
import xyz.atomland.atomized.config.ConfigManager;
import xyz.atomland.atomized.core.CompatRegistry;
import xyz.atomland.atomized.core.PanicSwitch;

/**
 * Lazily initialized shared state that must exist before the client entrypoint runs:
 * the mixin plugin consults the config and compat rules while mixins are being applied,
 * long before {@link Atomized#onInitializeClient()}.
 *
 * <p>The bootstrap config manager is created without registered modules (they do not
 * exist yet at mixin-apply time); unknown module entries fall back to
 * {@link xyz.atomland.atomized.core.ModuleIds} defaults for gating purposes. The
 * entrypoint later re-reads the config with the real module list.
 */
public final class AtomizedBootstrap {
    private static volatile Holder instance;

    private AtomizedBootstrap() {
    }

    public static Holder get() {
        Holder holder = instance;
        if (holder == null) {
            synchronized (AtomizedBootstrap.class) {
                holder = instance;
                if (holder == null) {
                    holder = create();
                    instance = holder;
                }
            }
        }
        return holder;
    }

    private static Holder create() {
        FabricLoader loader = FabricLoader.getInstance();
        String mcVersion = loader.getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        Path configFile = loader.getConfigDir().resolve("atomized.json");
        ConfigManager configManager = new ConfigManager(configFile, List.of());
        AtomizedConfig config = configManager.load();
        CompatRegistry compat = CompatRegistry.load(loader::isModLoaded);
        return new Holder(configManager, config, compat, new PanicSwitch(), mcVersion);
    }

    /**
     * @param configManager early config manager (no module defaults)
     * @param earlyConfig the config as loaded at bootstrap time
     * @param compat compat decisions for the loaded mod set
     * @param panicSwitch the global fail-soft incident sink
     * @param minecraftVersion the running Minecraft version
     */
    public record Holder(ConfigManager configManager, AtomizedConfig earlyConfig, CompatRegistry compat,
                         PanicSwitch panicSwitch, String minecraftVersion) {
    }
}
