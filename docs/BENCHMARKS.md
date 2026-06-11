# Atomized Benchmarks

> No performance claim is published unless proven here (master plan §2, §15).
> Measurements are taken locally before every stable release using the fixed-scene
> protocol below; CI only gates correctness (≥300 tests + mixin audit).

## Protocol summary

- **Scenes:** S1 empty superflat (regression check), S2 200-mob farm, S3 particle storm,
  S4 large village + 32-chunk tour (scripted camera path), S5 elytra chunk-loading run.
- **Configurations:** Vanilla / Vanilla+Atomized / Sodium / Sodium+Atomized /
  Sodium+(Lithium+FerriteCore+ImmediatelyFast)+Atomized.
- **Measurement:** `/atomized bench 60` → CSV (avg FPS, 1% low, 0.1% low, frametime stddev,
  alloc MB/s); 3 runs, median taken; compared with `tools/bench_compare.py`.

## Acceptance thresholds (per stable release)

| Metric | Scenario | Target |
|---|---|---|
| Average FPS | Sodium vs Sodium+Atomized, entity-heavy | ≥ +10% |
| 1% low FPS | same | ≥ +15% |
| Frametime stddev | tour scene | ≥ −25% |
| Allocation rate | HUD/inventory-heavy | ≥ −15% |
| Empty superflat regression | Sodium vs Sodium+Atomized | ≤ −1% |
| Standalone vs vanilla | entity-heavy | ≥ +20% avg FPS |

## Results

_No releases yet._
