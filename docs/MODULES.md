# Atomized Modules

> Status legend: 🟢 implemented · 🟡 in progress · ⚪ planned
> See `ATOMIZED_MASTER_PLAN.md` §6 for full specifications.

| Module | Status | Default | What it does | Auto-off when detected |
|---|---|---|---|---|
| `smart_culling` | ⚪ planned | ON | Entity & BlockEntity occlusion culling via async voxel raycasts | EntityCulling, MoreCulling |
| `particle_control` | 🟢 live | ON | Per-tick new-particle budget (burst limiter) | ParticleCore-family (partial) |
| `frame_pacing` | 🟡 core done | ON | Precise hybrid park/spin FPS pacer, jitter reduction | VulkanMod |
| `load_governor` | 🟡 core done | ON | Temporary, gradual quality scaling during frame-time spikes (no mixins) | — |
| `idle_throttle` | 🟢 live | ON | FPS throttling when the window is unfocused | Dynamic FPS |
| `memory_relief` | ⚪ planned | ON | Allocation-churn reduction in hot client paths | memoryleakfix / ImmediatelyFast (partial) |
| `gui_opt` | ⚪ planned | ON | Screen/HUD cost reduction (caching, debounce) | ImmediatelyFast (partial) |
| `chunk_smooth` | ⚪ planned | ON | Per-frame budget for chunk-packet application | C2ME (client) |
| `sp_boost` | ⚪ planned | ON | Singleplayer autosave smoothing | smoothchunksave, ksyxis (partial) |
| `diagnostics` | 🟢 live | HUD off | Live frametime sampling + lag-spike logger (HUD/bench pending) | — |

### Implemented so far

- **Core runtime (M1):** module lifecycle (`ModuleManager`), data-driven conflict gating
  (`CompatRegistry` + `atomized_compat_rules.json`), fail-soft `PanicSwitch`, versioned
  config with corrupt-file recovery and hot-reload, the gating mixin plugin, and the
  `/atomized [status|reload]` command.
- **Settings GUI (M2):** version-stable fallback config screen, ModMenu entry, and an
  unbound "Atomized Settings" keybind. (Embedding the page inside Sodium's video settings
  is still to come.)
- **`frame_pacing` / `diagnostics` cores (M3):** `FrameClock` (tested park/spin pacer math)
  and `FrameTimeStats` (tested frametime statistics: average/1%/0.1% low FPS, jitter). The
  per-frame sampling and limiter mixins that drive them at runtime are the next step.

`🟡 core done` means the version-independent logic is implemented and unit-tested; the
client-side mixin/event wiring that activates it in-game is still pending.

Documentation for each module (config keys, mixin targets, exemption rules) is added here
as the module is implemented.
