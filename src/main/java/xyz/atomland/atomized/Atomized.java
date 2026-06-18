package xyz.atomland.atomized;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.atomland.atomized.command.AtomizedCommand;
import xyz.atomland.atomized.core.ModuleManager;

/**
 * Client entrypoint for Atomized.
 *
 * <p>Atomized is a client-side performance mod designed to run alongside Sodium,
 * covering the areas Sodium does not touch. Modules are registered and gated
 * here; see {@code ATOMIZED_MASTER_PLAN.md} for the full architecture.
 */
public final class Atomized implements ClientModInitializer {
    public static final String MOD_ID = "atomized";
    public static final Logger LOGGER = LoggerFactory.getLogger("Atomized");

    /** How often (in client ticks) the config file is polled for hot-reload. */
    private static final int CONFIG_POLL_INTERVAL_TICKS = 60;

    private static ModuleManager moduleManager;
    private int ticksUntilConfigPoll = CONFIG_POLL_INTERVAL_TICKS;

    @Override
    public void onInitializeClient() {
        AtomizedBootstrap.Holder boot = AtomizedBootstrap.get();
        FabricLoader loader = FabricLoader.getInstance();

        ModuleManager manager = new ModuleManager(boot.panicSwitch(), loader::isModLoaded, boot.minecraftVersion());
        // Optimization modules are registered here as they land (milestones M3/M4).
        manager.initAll(boot.configManager().current(), boot.compat());
        boot.configManager().addListener(manager::onConfigChange);
        moduleManager = manager;

        AtomizedCommand.register(manager, boot.configManager(), boot.compat());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            manager.tickAll();
            if (--ticksUntilConfigPoll <= 0) {
                ticksUntilConfigPoll = CONFIG_POLL_INTERVAL_TICKS;
                boot.configManager().reloadIfChanged();
            }
        });

        LOGGER.info("Atomized initialized on Minecraft {}. Smooth by design.", boot.minecraftVersion());
    }

    /** The live module manager, once the client entrypoint has run. */
    public static ModuleManager moduleManager() {
        return moduleManager;
    }
}
