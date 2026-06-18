package xyz.atomland.atomized.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import xyz.atomland.atomized.config.ConfigManager;
import xyz.atomland.atomized.core.CompatRegistry;
import xyz.atomland.atomized.core.ModuleManager;
import xyz.atomland.atomized.core.ModuleStatus;

/**
 * The {@code /atomized} client command (master plan §9): module status overview and
 * config reload. Purely client-side; never sends anything to the server.
 */
public final class AtomizedCommand {
    private AtomizedCommand() {
    }

    public static void register(ModuleManager manager, ConfigManager configManager, CompatRegistry compat) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("atomized")
                        .executes(ctx -> status(ctx.getSource(), manager, compat))
                        .then(ClientCommandManager.literal("status")
                                .executes(ctx -> status(ctx.getSource(), manager, compat)))
                        .then(ClientCommandManager.literal("reload")
                                .executes(ctx -> reload(ctx.getSource(), configManager)))));
    }

    private static int status(FabricClientCommandSource source, ModuleManager manager, CompatRegistry compat) {
        source.sendFeedback(Component.translatable("atomized.command.status.header"));
        for (ModuleStatus status : manager.statuses()) {
            source.sendFeedback(switch (status.state()) {
                case ENABLED -> Component.translatable("atomized.command.status.enabled", status.moduleId());
                case DISABLED_CONFIG -> Component.translatable("atomized.command.status.disabled_config", status.moduleId());
                case DISABLED_CONFLICT -> Component.translatable("atomized.command.status.disabled_conflict",
                        status.moduleId(), compat.displayName(status.detail()));
                case DISABLED_VERSION -> Component.translatable("atomized.command.status.disabled_version",
                        status.moduleId(), status.detail());
                case FAILED -> Component.translatable("atomized.command.status.failed", status.moduleId(), status.detail());
            });
        }
        if (manager.statuses().isEmpty()) {
            source.sendFeedback(Component.translatable("atomized.command.status.no_modules"));
        }
        return 1;
    }

    private static int reload(FabricClientCommandSource source, ConfigManager configManager) {
        configManager.forceReload();
        source.sendFeedback(Component.translatable("atomized.command.reloaded"));
        return 1;
    }
}
