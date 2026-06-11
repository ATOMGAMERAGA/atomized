package xyz.atomland.atomized;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void onInitializeClient() {
        LOGGER.info("Atomized initialized. Smooth by design.");
    }
}
