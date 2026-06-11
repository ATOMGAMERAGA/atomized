# Atomized Modules

> Status legend: 🟢 implemented · 🟡 in progress · ⚪ planned
> See `ATOMIZED_MASTER_PLAN.md` §6 for full specifications.

| Module | Status | Default | What it does | Auto-off when detected |
|---|---|---|---|---|
| `smart_culling` | ⚪ planned | ON | Entity & BlockEntity occlusion culling via async voxel raycasts | EntityCulling, MoreCulling |
| `particle_control` | ⚪ planned | ON | Particle budget, off-screen/distance culling | ParticleCore-family (partial) |
| `frame_pacing` | ⚪ planned | ON | Precise hybrid park/spin FPS pacer, jitter reduction | VulkanMod |
| `load_governor` | ⚪ planned | ON | Temporary, gradual quality scaling during frame-time spikes (no mixins) | — |
| `idle_throttle` | ⚪ planned | ON | FPS/audio throttling when unfocused or minimized | Dynamic FPS |
| `memory_relief` | ⚪ planned | ON | Allocation-churn reduction in hot client paths | memoryleakfix / ImmediatelyFast (partial) |
| `gui_opt` | ⚪ planned | ON | Screen/HUD cost reduction (caching, debounce) | ImmediatelyFast (partial) |
| `chunk_smooth` | ⚪ planned | ON | Per-frame budget for chunk-packet application | C2ME (client) |
| `sp_boost` | ⚪ planned | ON | Singleplayer autosave smoothing | smoothchunksave, ksyxis (partial) |
| `diagnostics` | ⚪ planned | HUD off | Frametime HUD, lag-spike logger, `/atomized bench`, JVM advisor | — |

Documentation for each module (config keys, mixin targets, exemption rules) is added here as the module is implemented.
