package xyz.atomland.atomized.mixinsupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import xyz.atomland.atomized.AtomizedBootstrap;
import xyz.atomland.atomized.core.ModuleIds;

/**
 * Applies each mixin only when (a) its owning module is enabled in the config,
 * (b) no conflicting mod disables that module, and (c) any version rule matches
 * (master plan §4). A disabled module therefore has exactly zero bytecode footprint.
 *
 * <p>Version-specific mixin class prefixes are registered in {@link #VERSION_RULES}
 * as those mixins are introduced (M3/M4).
 */
public final class AtomizedMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> VERSION_RULES = Map.of();

    private MixinGate gate;

    @Override
    public void onLoad(String mixinPackage) {
        AtomizedBootstrap.Holder boot = AtomizedBootstrap.get();
        this.gate = new MixinGate(
                mixinPackage,
                moduleId -> boot.earlyConfig().isEnabled(moduleId, ModuleIds.defaultEnabled(moduleId))
                        && boot.compat().disabledBy(moduleId).isEmpty(),
                boot.minecraftVersion(),
                VERSION_RULES);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return gate.shouldApply(mixinClassName);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
