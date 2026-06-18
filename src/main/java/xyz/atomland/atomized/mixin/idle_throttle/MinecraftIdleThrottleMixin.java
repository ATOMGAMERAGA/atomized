package xyz.atomland.atomized.mixin.idle_throttle;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.atomland.atomized.modules.idlethrottle.IdleThrottleModule;

/**
 * Throttles the frame rate at the end of each frame while the window is unfocused
 * (master plan §6.5).
 *
 * <p>Conflict-safety: {@code Minecraft} is in Sodium's injectable-overlap set (runTick is
 * never rewritten). This is a chainable {@link Inject} at {@code TAIL} — it coexists with
 * the diagnostics frame-sampling inject on the same method and with any other mod's hooks.
 * {@code require = 0} makes it fail-soft. It only parks the client thread when unfocused;
 * it changes no game state and sends nothing to a server.
 */
@Mixin(Minecraft.class)
public class MinecraftIdleThrottleMixin {
    @Inject(method = "runTick", at = @At("TAIL"), require = 0)
    private void atomized$idleThrottle(boolean renderLevel, CallbackInfo ci) {
        IdleThrottleModule module = IdleThrottleModule.active();
        if (module != null) {
            module.onFrameEnd(((Minecraft) (Object) this).isWindowActive());
        }
    }
}
