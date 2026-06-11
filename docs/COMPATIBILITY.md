# Atomized Compatibility

> This document tracks the compatibility matrix (master plan §8) and the
> "do-not-touch map" of code regions rewritten by Sodium (§7).
> It is filled in as modules are implemented (milestone M5 hardens it in CI).

## Matrix (target behavior)

| Mod | Relationship | Atomized behavior |
|---|---|---|
| Sodium | Integration | Settings page inside Sodium's Video Settings; all modules compatible |
| Iris (+ shaders) | Tested | No overlap; `smart_culling` switches to shadow-safe mode when shaders are on |
| Lithium | Compatible | No intersection |
| FerriteCore | Compatible | Different lane (static footprint vs allocation churn) |
| EntityCulling | Territory overlap | `smart_culling` auto-OFF |
| MoreCulling | Territory overlap | `smart_culling` auto-OFF |
| ImmediatelyFast | Partial | `gui_opt`/HUD-cache sub-features OFF |
| Dynamic FPS | Territory overlap | `idle_throttle` OFF |
| ModernFix | Compatible | No intersection |
| C2ME | Partial | `chunk_smooth` OFF |
| Krypton | Compatible | Different layer |
| Reese's Sodium Options / Sodium Options API | Integration | Settings page added via this API |
| ModMenu | Integration | Config screen entry |
| OptiFabric/OptiFine | **breaks** | Refuses to load together |
| VulkanMod | conflicts | Warning + render-adjacent modules OFF |

## Sodium do-not-touch map

_To be generated during implementation by scanning Sodium's `*.mixins.json`
for each supported Sodium version; the CI mixin audit enforces it._
