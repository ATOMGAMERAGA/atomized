package xyz.atomland.atomized.mixin.particle_control;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.atomland.atomized.modules.particlecontrol.ParticleBudget;
import xyz.atomland.atomized.modules.particlecontrol.ParticleControlModule;

/**
 * Enforces the per-tick new-particle budget (master plan §6.2).
 *
 * <p>Conflict-safety: {@code ParticleEngine} is touched by neither Sodium nor any scanned
 * mod — a fully clean target. Both injectors are chainable {@link Inject}s (one cancellable),
 * never {@code @Overwrite}/{@code @Redirect}, and {@code require = 0} so a moved target just
 * disables the feature. Behaviour change is limited to <em>declining to spawn excess</em>
 * particles during a burst; it never alters game logic or anything sent to a server.
 *
 * <p>The {@code add}/{@code tick} method names are stable across 1.21.1–1.21.11 (verified),
 * so a single mixin serves every target.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void atomized$resetBudget(CallbackInfo ci) {
        ParticleControlModule module = ParticleControlModule.active();
        if (module != null && module.budget() != null) {
            module.budget().beginTick();
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void atomized$enforceBudget(Particle particle, CallbackInfo ci) {
        ParticleControlModule module = ParticleControlModule.active();
        if (module == null) {
            return;
        }
        ParticleBudget budget = module.budget();
        if (budget != null && !budget.tryAdd()) {
            ci.cancel();
        }
    }
}
