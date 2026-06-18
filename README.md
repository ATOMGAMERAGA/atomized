<div align="center">

<img src="src/main/resources/assets/atomized/icon.png" alt="Atomized" width="128" height="128">

# ⚛️ Atomized

**Smooth by design.** / **Tasarımı gereği akıcı.**

A complementary client-side performance mod for Fabric — designed to run flawlessly **alongside** Sodium, covering the areas Sodium does not touch.

[![CI](https://github.com/ATOMGAMERAGA/Atomized/actions/workflows/ci.yml/badge.svg)](https://github.com/ATOMGAMERAGA/Atomized/actions/workflows/ci.yml)

[Website](https://atomland.xyz) · [Issues](https://github.com/ATOMGAMERAGA/Atomized/issues) · [Master Plan](ATOMIZED_MASTER_PLAN.md)

</div>

---

## 🇬🇧 English

### What is Atomized?

Atomized optimizes the performance areas Sodium intentionally leaves alone: entity/particle load, frame pacing, allocation churn, GUI cost and chunk-packet stutter. It raises average FPS, noticeably reduces 1% lows (FPS drops), and is engineered to **never conflict with other mods** (MixinExtras-only injections, automatic module gating when an overlapping mod is detected).

- **Minecraft:** Java 1.21 → 1.21.11 (the entire Java 21 era)
- **Platform:** Fabric, client-side only — safe on servers, never touches game logic or outgoing packets
- **Works with:** Sodium (recommended), Iris, Lithium, FerriteCore, ModMenu and more
- **Telemetry:** none. Atomized never collects or sends any data.

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.16) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the Atomized jar matching your Minecraft version into `mods/`.
3. (Recommended) Install [Sodium](https://modrinth.com/mod/sodium) — Atomized adds its settings page right below Sodium's in Video Settings.

### Status

🚧 **In development.** See [ATOMIZED_MASTER_PLAN.md](ATOMIZED_MASTER_PLAN.md) for the roadmap and [docs/MODULES.md](docs/MODULES.md) for module documentation. Performance claims are only published after they are proven by the benchmark protocol in [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

---

## 🇹🇷 Türkçe

### Atomized nedir?

Atomized, Sodium'un bilinçli olarak dokunmadığı performans alanlarını optimize eder: entity/parçacık yükü, frame pacing, bellek çöpü (allocation churn), GUI maliyeti ve chunk-paketi stutter'ı. Ortalama FPS'i yükseltir, %1 low'ları (FPS droplarını) belirgin azaltır ve **hiçbir modla çakışmayacak** şekilde tasarlanmıştır (yalnızca MixinExtras enjeksiyonları, örtüşen mod algılandığında otomatik modül kapatma).

- **Minecraft:** Java 1.21 → 1.21.11 (Java 21 döneminin tamamı)
- **Platform:** Fabric, yalnızca istemci tarafı — sunucularda güvenlidir, oyun mantığına ve giden paketlere asla dokunmaz
- **Birlikte çalışır:** Sodium (önerilir), Iris, Lithium, FerriteCore, ModMenu ve dahası
- **Telemetri:** yok. Atomized hiçbir veri toplamaz ve göndermez.

### Kurulum

1. [Fabric Loader](https://fabricmc.net/use/) (≥ 0.16) ve [Fabric API](https://modrinth.com/mod/fabric-api) kurun.
2. Minecraft sürümünüze uygun Atomized jar dosyasını `mods/` klasörüne atın.
3. (Önerilir) [Sodium](https://modrinth.com/mod/sodium) kurun — Atomized, ayar sayfasını Video Settings'te Sodium'un sayfalarının hemen altına ekler.

### Durum

🚧 **Geliştirme aşamasında.** Yol haritası için [ATOMIZED_MASTER_PLAN.md](ATOMIZED_MASTER_PLAN.md), modül dokümantasyonu için [docs/MODULES.md](docs/MODULES.md) dosyasına bakın.

---

## Development

```bash
# Build all 7 targets (12 Minecraft versions)
./gradlew chiseledBuild

# Run the unit test suite for all targets
./gradlew chiseledTest

# Build a single target
./gradlew :1.21.11:build
```

| Target jar | Covers Minecraft |
|---|---|
| `atomized-<v>+mc1.21.1.jar`  | 1.21 – 1.21.1 |
| `atomized-<v>+mc1.21.3.jar`  | 1.21.2 – 1.21.3 |
| `atomized-<v>+mc1.21.4.jar`  | 1.21.4 |
| `atomized-<v>+mc1.21.5.jar`  | 1.21.5 |
| `atomized-<v>+mc1.21.8.jar`  | 1.21.6 – 1.21.8 |
| `atomized-<v>+mc1.21.10.jar` | 1.21.9 – 1.21.10 |
| `atomized-<v>+mc1.21.11.jar` | 1.21.11 |

> Maintainer note: enable branch protection on `main` ("require CI green before merge") in the repository settings.

## License

[BSD 3-Clause](LICENSE) © Atom Gamer Arda A.G.A — the repository ships with the BSD 3-Clause license chosen by the author (the master plan's MIT default was superseded by the license already committed to this repository).
