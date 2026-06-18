package xyz.atomland.atomized.mixinsupport;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinGateTest {

    private static final String ROOT = "xyz.atomland.atomized.mixin";

    private static MixinGate gate(Set<String> enabledModules, String mcVersion, Map<String, String> versionRules) {
        return new MixinGate(ROOT, enabledModules::contains, mcVersion, versionRules);
    }

    @ParameterizedTest
    @CsvSource({
            "xyz.atomland.atomized.mixin.frame_pacing.MinecraftMixin, frame_pacing",
            "xyz.atomland.atomized.mixin.smart_culling.entity.DispatcherMixin, smart_culling",
            "xyz.atomland.atomized.mixin.gui_opt.TooltipMixin, gui_opt",
            "xyz.atomland.atomized.mixin.core.BootstrapMixin, core",
            "xyz.atomland.atomized.mixin.plugin.SomethingInternal, core",
            "xyz.atomland.atomized.mixin.RootLevelMixin, core",
            "com.example.other.mixin.ForeignMixin, core"
    })
    void moduleOfFollowsPackageConvention(String mixinClass, String expectedModule) {
        MixinGate gate = gate(Set.of(), "1.21.11", Map.of());
        assertEquals(expectedModule, gate.moduleOf(mixinClass));
    }

    @Test
    void trailingDotOnRootPackageIsTolerated() {
        MixinGate gate = new MixinGate(ROOT + ".", Set.of("frame_pacing")::contains, "1.21.11", Map.of());
        assertEquals("frame_pacing", gate.moduleOf(ROOT + ".frame_pacing.SomeMixin"));
        assertTrue(gate.shouldApply(ROOT + ".frame_pacing.SomeMixin"));
    }

    @Test
    void enabledModuleMixinApplies() {
        MixinGate gate = gate(Set.of("frame_pacing"), "1.21.11", Map.of());
        assertTrue(gate.shouldApply(ROOT + ".frame_pacing.MinecraftMixin"));
    }

    @Test
    void disabledModuleMixinDoesNotApply() {
        MixinGate gate = gate(Set.of(), "1.21.11", Map.of());
        assertFalse(gate.shouldApply(ROOT + ".frame_pacing.MinecraftMixin"));
    }

    @Test
    void coreMixinsAlwaysApply() {
        MixinGate gate = gate(Set.of(), "1.21.11", Map.of());
        assertTrue(gate.shouldApply(ROOT + ".core.BootstrapMixin"));
        assertTrue(gate.shouldApply(ROOT + ".RootLevelMixin"));
    }

    @Test
    void versionRuleBlocksOutOfRangeMixin() {
        Map<String, String> rules = Map.of(ROOT + ".chunk_smooth.v2.", ">=1.21.5");
        MixinGate oldMc = gate(Set.of("chunk_smooth"), "1.21.1", rules);
        MixinGate newMc = gate(Set.of("chunk_smooth"), "1.21.8", rules);

        assertFalse(oldMc.shouldApply(ROOT + ".chunk_smooth.v2.PacketMixin"));
        assertTrue(newMc.shouldApply(ROOT + ".chunk_smooth.v2.PacketMixin"));
        // Sibling mixins without a rule are unaffected.
        assertTrue(oldMc.shouldApply(ROOT + ".chunk_smooth.QueueMixin"));
    }

    @Test
    void versionRuleAppliesToCoreMixinsToo() {
        Map<String, String> rules = Map.of(ROOT + ".core.New", ">=1.21.9");
        MixinGate gate = gate(Set.of(), "1.21.4", rules);
        assertFalse(gate.shouldApply(ROOT + ".core.NewThingMixin"));
        assertTrue(gate.shouldApply(ROOT + ".core.OldThingMixin"));
    }

    @Test
    void throwingModulePredicateMeansDoNotApply() {
        MixinGate gate = new MixinGate(ROOT, module -> {
            throw new IllegalStateException("config exploded");
        }, "1.21.11", Map.of());
        assertFalse(gate.shouldApply(ROOT + ".frame_pacing.MinecraftMixin"));
        // Core mixins skip the predicate entirely and still apply.
        assertTrue(gate.shouldApply(ROOT + ".core.BootstrapMixin"));
    }

    @Test
    void malformedVersionRuleMeansDoNotApply() {
        Map<String, String> rules = Map.of(ROOT + ".gui_opt.", ">=banana");
        MixinGate gate = gate(Set.of("gui_opt"), "1.21.11", rules);
        assertFalse(gate.shouldApply(ROOT + ".gui_opt.TooltipMixin"));
    }

    @Test
    void multipleVersionRulesAreAllChecked() {
        Map<String, String> rules = Map.of(
                ROOT + ".sp_boost.", ">=1.21",
                ROOT + ".sp_boost.modern.", ">=1.21.9");
        MixinGate gate = gate(Set.of("sp_boost"), "1.21.4", rules);
        assertTrue(gate.shouldApply(ROOT + ".sp_boost.SaveMixin"));
        assertFalse(gate.shouldApply(ROOT + ".sp_boost.modern.SaveMixin"));
    }
}
