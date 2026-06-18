# Atomized Compatibility

Atomized is engineered to **never conflict with any other mod**. This is not a hope —
it rests on four mechanically-enforced rules and a body of verified data.

## The non-conflict guarantee (how, not just "trust us")

1. **Chainable injectors only.** Every Atomized mixin uses MixinExtras
   (`@WrapOperation`, `@ModifyExpressionValue`, `@WrapWithCondition`, `@Inject`) — never
   `@Overwrite` or `@Redirect`. Chainable injectors compose, so two mods touching the same
   method coexist. This holds against **every** mod, including ones that don't exist yet.
   Enforced by `MixinAuditTest` on every build (proven to fail on a planted violation).
2. **Never touch Sodium's rewritten code.** The do-not-touch map (`atomized_sodium_regions.json`)
   is generated from Sodium's own mixin configs across all 7 supported MC versions. The audit
   fails the build if any Atomized mixin targets a Sodium-rewritten class.
3. **Auto-disable on same-purpose mods.** When a mod that already does a job is present,
   Atomized's overlapping module turns itself off (data-driven, `atomized_compat_rules.json`).
   Mod ids were verified against each mod's real `fabric.mod.json`.
4. **No gameplay/packet changes.** Atomized only affects the visual/performance layer; it never
   alters game logic or anything sent to the server, so it is anti-cheat safe.

`require = 0` on every injector makes a missing target fail-soft: the mixin simply doesn't
apply and the rest of the mod is unaffected.

## Verified mod-id gates (`atomized_compat_rules.json`)

Verified 2026-06-18 against each mod's current 1.21.x release on Modrinth:

| Mod | Real fabric id | Atomized behavior |
|---|---|---|
| EntityCulling | `entityculling` ✓ | `smart_culling` auto-OFF |
| MoreCulling | `moreculling` ✓ | `smart_culling` auto-OFF |
| Dynamic FPS | `dynamic_fps` ✓ | `idle_throttle` auto-OFF |
| C2ME | `c2me` ✓ | `chunk_smooth` auto-OFF |
| VulkanMod | `vulkanmod` ✓ | all render-adjacent modules OFF |
| ImmediatelyFast | `immediatelyfast` ✓ | `gui_opt`/`memory_relief` HUD sub-features OFF |
| ParticleCore | `particle_core` ✓ | `particle_control` ticking-skip OFF |
| Particle Rain | `particlerain` ✓ | `particle_control` ticking-skip OFF |

> Two id bugs were found and fixed during this verification: the rules previously said
> `particlecore`/`particle_rain`, but the real ids are `particle_core`/`particlerain`.
> Locked down by `CompatRegistryTest`.

## Verified mixin-target overlap with other mods

Real mixin targets were extracted from the current 1.21.11 builds of the major mods
(`tools/foreign_mixin_targets.json`). For each class Atomized injects into (now or planned),
the other mods that also touch it:

| Atomized target | Module | Also touched by | Safe because |
|---|---|---|---|
| `Minecraft#runTick` | `diagnostics` (live) | Dynamic FPS | chainable `@Inject`, pure measurement; `idle_throttle` also auto-OFF under Dynamic FPS |
| `ParticleEngine` | `particle_control` (planned) | *nobody* | clean target |
| `EntityRenderDispatcher` | `smart_culling` (planned) | *nobody* | clean; also auto-OFF under EntityCulling/MoreCulling |
| `ClientPacketListener` | `chunk_smooth` (planned) | Lithium | chainable injectors compose; also auto-OFF under C2ME |
| `LevelChunk` | `sp_boost` (planned) | Lithium | chainable injectors compose |
| `Screen` | `gui_opt` (planned) | *nobody* | clean target |

Mods scanned and confirmed compatible (no problematic overlap): Lithium, EntityCulling,
ImmediatelyFast, Dynamic FPS, Krypton, Sodium Extra, Reese's Sodium Options, Continuity,
Iris, LambDynamicLights. (ModernFix, Indium, MemoryLeakFix have no current 1.21.11 Fabric
build; Indium is obsolete since Sodium 0.6 ships FRAPI.)

## Sodium do-not-touch map (`atomized_sodium_regions.json`)

Generated from Sodium's mixin configs across MC 1.21.1–1.21.11 (`tools/scan_sodium_regions.py`,
`tools/gen_sodium_regions.py`):

- **84 forbidden classes** Sodium rewrites — Atomized never mixins these: the render pipeline
  (`LevelRenderer`, `GameRenderer`, `FogRenderer`, `CloudRenderer`), vertex/buffer
  (`BufferBuilder`, `VertexConsumer`, `MeshData`, `VertexFormat`), models
  (`BlockRenderDispatcher`, `ModelBlockRenderer`, `BakedModel`/`BakedQuad`), textures/atlas,
  chunk storage (`PalettedContainer`, `*BitStorage`), GL state, sky/particle render, etc.
- **9 injectable-overlap classes** Sodium only hooks at lifecycle points — Atomized may use
  them with chainable injectors only: `Minecraft`, `ClientPacketListener`, `ClientChunkCache`,
  `EntityRenderDispatcher`, `Gui`, `DebugScreenOverlay`, `OptionsScreen`,
  `LevelLoadStatusManager`, `LevelLoadingScreen`.

Regenerate after a Sodium update with the scripts in `tools/`.
