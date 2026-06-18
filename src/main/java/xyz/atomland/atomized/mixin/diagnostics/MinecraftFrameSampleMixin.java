package xyz.atomland.atomized.mixin.diagnostics;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.atomland.atomized.diagnostics.DiagnosticsModule;

/**
 * Feeds real per-frame timings to the {@code diagnostics} module (master plan §6.10).
 *
 * <p>Conflict-safety: {@code Minecraft} is in Sodium's injectable-overlap set (Sodium only
 * hooks its lifecycle/window state, never rewrites {@code runTick}). This is a pure
 * {@link Inject} at {@code HEAD} — a chainable injector that changes <em>nothing</em>: it
 * only reads {@link System#nanoTime()} and records the inter-frame interval. It cannot
 * affect behaviour, rendering, or any other mod's mixin on the same method. {@code require = 0}
 * makes it fail-soft: if the target ever moves, the mixin simply does not apply and the rest
 * of the mod is unaffected.
 */
@Mixin(Minecraft.class)
public class MinecraftFrameSampleMixin {
    @Unique
    private long atomized$lastFrameNanos;

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void atomized$sampleFrameTime(boolean renderLevel, CallbackInfo ci) {
        long now = System.nanoTime();
        long last = atomized$lastFrameNanos;
        atomized$lastFrameNanos = now;
        if (last == 0L) {
            return;
        }
        DiagnosticsModule diagnostics = DiagnosticsModule.active();
        if (diagnostics != null) {
            diagnostics.recordFrame(now - last);
        }
    }
}
