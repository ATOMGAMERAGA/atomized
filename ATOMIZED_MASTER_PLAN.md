# ⚛️ ATOMIZED — Master Geliştirme Planı

> **Bu doküman, Claude Code (Fable 5) tarafından sıfırdan uygulanmak üzere hazırlanmış eksiksiz bir mühendislik planıdır.**
> Repo köküne `ATOMIZED_MASTER_PLAN.md` olarak koyulur. Claude Code bu dosyayı tek doğruluk kaynağı (source of truth) kabul eder ve M0'dan başlayarak kilometre taşlarını sırayla uygular.

---

## 0. Claude Code İçin Çalışma Talimatları

1. Bu planı baştan sona oku, sonra **Bölüm 17'deki kilometre taşlarını sırayla** uygula. Bir kilometre taşının "Bitti Tanımı (DoD)" sağlanmadan sonrakine geçme.
2. Plandaki sürüm numaraları (Loom, Stonecutter, Fabric API, Sodium, action sürümleri) **taslaktır** — implementasyon sırasında her bağımlılığın **gerçek güncel sürümünü** resmî kaynaktan (Modrinth API, FabricMC maven, kikugie maven) çözümle ve `gradle.properties` dosyalarına pinle. Asla tahmini sürüm yazma.
3. **Bölüm 5'teki Altın Kurallar pazarlık konusu değildir.** Bir optimizasyon fikri bu kurallarla çelişiyorsa optimizasyonu uygulama, kuralı esnetme.
4. Her modül için önce testini yaz (test-first), sonra mixin/kodu yaz, sonra uyumluluk kapısını (compat gate) yaz.
5. Emin olmadığın her API detayı (ör. Sodium Options API sınıf adları, Fabric client gametest API imzaları) için ilgili kütüphanenin **gerçek kaynak kodunu/javadoc'unu indir ve doğrula**; ezberden yazma.
6. Kod, commit mesajları ve Javadoc **İngilizce**; kullanıcıya görünen metinler `en_us.json` + `tr_tr.json` lang dosyalarıyla **iki dilli**.

---

## 1. Proje Kimliği

| Alan | Değer |
|---|---|
| Mod adı | **Atomized** |
| Mod ID | `atomized` |
| Slogan | "Smooth by design." / "Tasarımı gereği akıcı." |
| Yapımcı | **Atom Gamer Arda A.G.A** |
| Web sitesi | **https://atomland.xyz** |
| Kaynak kod | GitHub (bu repo) |
| Logo / ikon | `https://r.resimlink.com/NjUcqe_I7f.png` → **indirilip repoya commit edilecek**: `src/main/resources/assets/atomized/icon.png` (512×512 PNG'ye normalize et; harici link asla runtime'da kullanılmaz, link ölürse mod etkilenmez). Aynı görsel README başlığında da kullanılır. |
| Java sürümü | **Java 21** (toolchain zorunlu) |
| Platform | **Fabric** (client-side only, `"environment": "client"`) |
| Paket | `xyz.atomland.atomized` |
| Lisans | Varsayılan **MIT** (Arda isterse LGPL-3.0'a çevrilebilir; karar README'de belirtilir) |
| Tür | Performans / optimizasyon modu — **Sodium'un yanında, Sodium'a ek olarak** çalışır |

### Sürüm kapsamı (kritik bağlam)

Hedef: **Minecraft Java 1.21 → 1.21.11 (dahil), yani Java 21 döneminin tamamı.**

- 1.21.11 ("Mounts of Mayhem", 9 Aralık 2025) **Java 21 gerektiren son sürüm ve eski `1.x.y` numaralandırmasını kullanan son sürümdür.** Bu yüzden Atomized 1.x serisi tam olarak bu aralığı kapsar — kapsam doğal olarak kapalıdır, "sürüm kovalamaca" yoktur.
- 1.21.11 sonrası MC sürümleri (yeni adlandırma, yeni Java gereksinimi) **Atomized 2.x** yol haritasıdır ve bu planın kapsamı dışındadır. Stonecutter mimarisi sayesinde ileride tek klasör ekleyerek genişletilebilir.

---

## 2. Misyon ve Ölçülebilir Başarı Kriterleri

**Misyon:** Sodium'un dokunmadığı performans alanlarını (entity/parçacık yükü, frame pacing, bellek çöpü, GUI maliyeti, chunk-paketi stutter'ı) optimize ederek; ortalama FPS'i yükselten, **%1 low'ları (FPS droplarını) belirgin azaltan** ve oyunu gözle görülür şekilde akıcılaştıran, **hiçbir modla çakışmayan**, kaya gibi stabil bir mod üretmek.

**Kabul kriterleri (her stable sürümde Bölüm 15 protokolüyle ölçülür ve `docs/BENCHMARKS.md`'ye işlenir):**

| Metrik | Senaryo | Hedef |
|---|---|---|
| Ortalama FPS | Sodium vs **Sodium+Atomized**, entity-yoğun sahne (200+ mob farm) | **≥ +%10** |
| %1 low FPS | Aynı sahne | **≥ +%15** |
| Frame-time std. sapması (jitter) | Sodium vs Sodium+Atomized, gezinti turu | **≥ −%25** |
| Allocation rate (MB/s) | HUD + envanter yoğun sahne | **≥ −%15** |
| Boş süperflat regresyonu | Sodium vs Sodium+Atomized | **≤ −%1 (regresyon yok)** |
| Crash / mixin apply hatası | Tüm uyumluluk matrisi (Bölüm 8) | **0** |
| Vanilla'ya kıyasla (Sodium'suz tek başına) | Entity-yoğun sahne | **≥ +%20 ort. FPS** |

> Bu hedeflerden herhangi biri tutmuyorsa sürüm **yayınlanmaz**; ilgili modül iyileştirilir veya varsayılanı kapatılır. "Gerçekten optimize ediyor" iddiası ölçümle kanıtlanır, pazarlamayla değil.

---

## 3. Teknik Kapsam

- **Mod loader:** Fabric Loader `>=0.16.x` (Loader 0.15+'tan beri **MixinExtras yerleşik** gelir — ayrıca JiJ gerekmez; yine de sürümü implementasyonda doğrula).
- **Fabric API:** her MC hedefi için ilgili güncel sürüm (versiyonlu `gradle.properties`).
- **Yan ortamlar:** Quilt'te "muhtemelen çalışır" ama resmî destek **yalnızca Fabric**. NeoForge bu sürümde kapsam dışı (mimari buna izin verecek şekilde modüler tutulur).
- **Bağımlılıklar (runtime):** Zorunlu: Fabric API. Opsiyonel/entegrasyon: Sodium, ModMenu, Sodium Options API (Bölüm 9). Atomized **Sodium olmadan da** tam çalışır.
- **Mappings:** **Mojang official mappings (mojmap)** — 12 alt sürüm boyunca isim stabilitesi Yarn'a göre çok daha yüksek, Stonecutter yükünü azaltır.

---

## 4. Mimari Genel Bakış

```
Atomized (ClientModInitializer)
 ├─ core/
 │   ├─ ModuleManager        → tüm modüllerin yaşam döngüsü (register → gate → init → tick)
 │   ├─ AtomizedModule       → arayüz: id(), conflictsWith(), defaultEnabled(), init(), onConfigChange()
 │   ├─ CompatRegistry       → yüklü modları tarar, çakışma kararlarını TEK yerden verir
 │   ├─ PanicSwitch          → herhangi bir modül init'te exception fırlatırsa: modülü kapat, logla,
 │   │                         oyuncuya toast göster — OYUNU ASLA ÇÖKERTME (fail-soft ilkesi)
 │   └─ VersionGuard         → MC sürümüne göre modül/mixin uygunluğu
 ├─ config/                  → JSON (Gson), `config/atomized.json`, hot-reload destekli
 ├─ mixin/plugin/AtomizedMixinPlugin (IMixinConfigPlugin)
 │                            → her mixin'i (a) modül açık mı (b) çakışan mod var mı (c) MC sürümü uygun mu
 │                              kontrollerinden geçirip uygular/uygulamaz
 ├─ compat/                  → SodiumOptionsIntegration, ModMenuIntegration (lazy-load, guard'lı)
 ├─ modules/                 → Bölüm 6'daki 10 modül, her biri kendi paketinde, birbirinden bağımsız
 ├─ diagnostics/             → bench harness, frametime HUD, lag-spike logger, JVM advisor
 └─ util/                    → MathPool, RaycastCache, FrameClock vb.
```

**Tasarım ilkeleri:**
- Her optimizasyon **bağımsız, tek tek aç/kapanabilir bir modüldür.** Bir modül kapalıysa onun mixin'leri **hiç uygulanmaz** (config startup'ta okunur; mixin gerektiren değişiklikler "yeniden başlatma gerekir" rozetiyle işaretlenir, runtime-toggle edilebilenler anında uygulanır).
- **Fail-soft:** modül başlatma hatası = modül kapanır + uyarı; asla crash.
- **Sıfır telemetri.** Atomized hiçbir veri toplamaz/göndermez.

---

## 5. Altın Kurallar (Çakışmasızlık Anayasası) — PAZARLIK YOK

1. **`@Overwrite` YASAK. `@Redirect` YASAK.** Tüm enjeksiyonlar MixinExtras ile: `@WrapOperation`, `@ModifyExpressionValue`, `@WrapWithCondition`, `@WrapMethod`, `@Local`. Bunlar zincirlenebilir olduğu için başka modların aynı noktaya dokunmasıyla çakışmaz — Atomized'ın "asla çakışmaz" iddiasının teknik temeli budur.
2. **Sodium'un yeniden yazdığı hiçbir koda dokunma:** terrain meshing, chunk render pipeline, `LevelRenderer`/`WorldRenderer`'ın Sodium tarafından değiştirilen yolları, vertex format/buffer yönetimi, fog/cloud render içleri. (Tam liste Bölüm 7.)
3. **Lithium/Krypton/ModernFix bölgesine girme:** sunucu tick mantığı, ağ sıkıştırma/netty pipeline, başlangıç-yükleme yeniden yapılandırmaları.
4. Aynı işi yapan ünlü bir mod yüklüyse Atomized'ın o modülü **otomatik kapanır** (CompatRegistry). Kullanıcıya log + GUI'de "X modu algılandı, bu modül devre dışı" notu gösterilir. Hard `breaks` SADECE gerçekten birlikte yaşayamayan modlara konur.
5. Her mixin **mojmap'te sürümler arası stabil** hedeflere (HEAD / RETURN / iyi bilinen INVOKE noktaları) enjekte edilir; kırılgan bytecode ofsetleri yasak.
6. Davranış değiştiren her şey **görsel/performans katmanında kalır** — oyun mantığını, hit/vuruş sonuçlarını, sunucuya giden paketleri **asla** değiştirme (anti-cheat güvenliği: GrimAC/TotemGuard'lı sunucularda dahi %100 güvenli olmalı).
7. Varsayılan config **"güvenli-hızlı"** profilidir: kanıtlanmış-güvenli modüller açık, agresif olanlar kapalı. "Aggressive" preset'i kullanıcı bilinçli seçer.
8. Her PR/commit CI'dan geçmeden release olamaz (Bölüm 14).

---

## 6. Optimizasyon Modülleri (Gerçek Kazanç Sağlayan İş Listesi)

> Her modül için: **Ne yapar → Nasıl (mixin hedefleri) → Çakışma kapısı → Beklenen kazanç → Varsayılan**.
> Mixin hedef isimleri mojmap'tir; Claude Code sürüm başına doğrular.

### 6.1 `smart_culling` — Entity & BlockEntity Occlusion Culling ⭐ (en büyük kazanç)
- **Ne:** Görüş hattında olmayan (duvar/yer arkasındaki) entity ve block entity'lerin render'ını atlar. Sodium chunk'ları culler ama **entity'leri raycast ile cullamaz** — bu alan boştur ve FPS etkisi devasadır (mob farm, köy, spawner odaları).
- **Nasıl:** Async thread'de oyuncu kamerasından entity AABB köşelerine ucuz voxel-raycast (önbellekli, blok değişiminde invalidate). Sonuç bir `visible` bayrağına yazılır; `EntityRenderDispatcher#shouldRender` / `BlockEntityRenderDispatcher` çağrıları `@WrapOperation` ile sarılır ve görünmeyenler atlanır. Gölge/iskelet güvenliği: hedeflenen entity, geçen X tick'te hasar alan/veren entity ve glowing entity'ler asla cullanmaz. Ses/parçacık üreten BE'ler (örn. çalışan furnace) için "tick ama render etme" modu.
- **Kapı:** `entityculling` veya `moreculling` yüklüyse → modül otomatik OFF (log + GUI notu).
- **Kazanç:** entity-yoğun sahnede %20–60 FPS.
- **Varsayılan:** AÇIK.

### 6.2 `particle_control` — Parçacık Bütçesi ve Culling
- **Ne:** Ekran dışı/çok uzak parçacıkları tickleme-render etmeme, parçacık sayısına dinamik üst sınır (bütçe), aynı pikselde yığılmış özdeş parçacıkları birleştirme.
- **Nasıl:** `ParticleEngine#tick` ve render döngüsünde `@WrapWithCondition`; bütçe aşıldığında en eski/dekoratif parçacıklar önce düşer (kritik olanlar — totem, patlama, redstone hatası teşhisi — korunur, beyaz liste).
- **Kapı:** `particlecore` / `particle-rain` gibi parçacık modları yüklüyse yalnızca **bütçe** alt-özelliği kalır, ticking-skip alt-özelliği kapanır.
- **Kazanç:** TNT/ışık seli/cadı çiftliği gibi anlarda drop'ların büyük kısmı yok olur (%1 low'a doğrudan etki).
- **Varsayılan:** AÇIK (bütçe: 4096, mesafe: 32 blok — config'te).

### 6.3 `frame_pacing` — Hassas FPS Limiter & Jitter Düzeltici ⭐ (smoothness'ın kalbi)
- **Ne:** Vanilla'nın kaba FPS limiter'ı yerine `LockSupport.parkNanos` + kısa spin-wait hibrit **hassas pacer**; kare süreleri eşitlenir → mikro-stutter ve frame-time jitter ciddi düşer. "120 FPS dalgalı" yerine "118 FPS cam gibi".
- **Nasıl:** `Minecraft#runTick`/framerate limiter çağrısı `@WrapOperation` ile Atomized `FrameClock`'a yönlendirilir; VSync açıkken pacer kendini devre dışı bırakır (çift senkron yasak). İsteğe bağlı "smoothing window": ani tek-kare spike'larında bir sonraki karenin sunumu mikro-geciktirilip algılanan akıcılık korunur.
- **Kapı:** Bilinen çakışan mod yok; yine de başka bir mod aynı limiter'ı sarmışsa MixinExtras zinciri sayesinde birlikte çalışır. VulkanMod yüklüyse OFF.
- **Kazanç:** ölçülebilir frame-time stddev −%25+; hissedilen akıcılıkta büyük fark.
- **Varsayılan:** AÇIK.

### 6.4 `load_governor` — Dinamik Yük Valisi (FPS drop sigortası)
- **Ne:** Frame-time hedefin üstüne çıktığında (drop anı) **geçici ve kademeli** olarak: entity render mesafesi %'sini düşürür, parçacık bütçesini kısar, süs animasyonlarını yavaşlatır; yük geçince saniyeler içinde kademeli geri açar. Kalite kaybı saliselik ve fark edilmesi zorken, drop'lar törpülenir.
- **Nasıl:** Diagnostics'in frame-time örnekleyicisini dinleyen saf-Java kontrolcü; oyun ayarlarına (entityDistanceScaling vb.) **vanilla option API'si üzerinden** dokunur, mixin gerekmez → çakışma riski sıfıra yakın.
- **Kapı:** yok (mixin'siz).
- **Kazanç:** %1 low'larda +%10–25.
- **Varsayılan:** AÇIK (yumuşak profil).

### 6.5 `idle_throttle` — Odak Dışı Tasarruf
- **Ne:** Pencere odak dışı → FPS 30'a, minimize → 1–5 FPS'e, ses kısma, toast erteleme. (Dynamic FPS modunun yaptığı iş.)
- **Kapı:** `dynamic_fps` yüklüyse → tamamen OFF.
- **Varsayılan:** AÇIK.

### 6.6 `memory_relief` — Allocation Churn Azaltma
- **Ne:** GC duraksamalarını (stutter'ın sinsi kaynağı) azaltmak için sıcak istemci yollarındaki gereksiz nesne üretimini keser: HUD/debug ekranı string ve liste yeniden-üretimlerini debounce+cache'leme, sık çağrılan render yardımcılarında `Vec3`/`Matrix4f` scratch havuzları (`@WrapOperation` ile yerel yeniden kullanım), tooltip sonuç cache'i, dünya değişiminde açık `System.gc()` ipucu **yalnızca** "dünyadan çıkış" anında (opsiyonel).
- **Kapı:** FerriteCore ile **çakışmaz** (o statik bellek *ayak izi*, bu *churn* — farklı şeritler); `memoryleakfix` yüklüyse leak-patch alt-özelliği kapanır. ImmediatelyFast yüklüyse HUD-cache alt-özelliği kapanır (6.7 ile ortak kapı).
- **Kazanç:** allocation rate −%15+, GC kaynaklı mikro-drop azalması.
- **Varsayılan:** AÇIK (gc-hint OFF).

### 6.7 `gui_opt` — Ekran/HUD Maliyet Düşürme
- **Ne:** Statik ekran arka planlarının cache'lenmesi, tooltip yeniden hesaplarının debounce'u, envanterde değişmeyen item modellerinin tekrar-bake edilmemesi.
- **Kapı:** `immediatelyfast` yüklüyse çakışan alt-özellikler OFF (IF'in HUD batching'i korunur; Atomized yalnızca IF'in yapmadığı debounce/cache kısımlarını çalıştırır — implementasyonda IF kaynak koduna bakıp kesişimi netleştir).
- **Varsayılan:** AÇIK (IF varken otomatik daralmış modda).

### 6.8 `chunk_smooth` — Chunk Paketi Stutter Kesici
- **Ne:** Sunucudan chunk yağdığı anlarda (giriş, teleport, elytra) ana thread'e binen chunk-paketi işleme yükünü **kare başına bütçeyle** yayar; tek karede 40 chunk işlemek yerine 4'er 4'er. Sodium meshing'i zaten async'tir; buradaki darboğaz **vanilla'nın paket→dünya uygulama** adımıdır ve Sodium'a dokunmaz.
- **Nasıl:** `ClientPacketListener`'ın chunk/level-chunk paket işleyicisinde `@WrapOperation` → Atomized kuyruğu (sıra korunur, ışık/komşu tutarlılığı gözetilir; oyuncunun altındaki chunk her zaman anında işlenir — boşluğa düşme yok).
- **Kapı:** `c2me` (client tarafı) yüklüyse OFF. Krypton ile katman farkı nedeniyle uyumlu.
- **Kazanç:** giriş/teleport drop'larında belirgin yumuşama.
- **Varsayılan:** AÇIK (muhafazakâr bütçe).

### 6.9 `sp_boost` — Tek Oyuncu (Integrated Server) Konforu
- **Ne:** Yalnızca tek oyunculuda: otomatik kaydetmenin kare düşürmeyen tamponlu/parçalı yazımı; F3+S benzeri ağır işlemlerin yayılması.
- **Kapı:** `smoothchunksave`, `ksyxis` vb. yüklüyse ilgili alt-özellik OFF. Çok oyunculu sunucularda modül tamamen pasiftir.
- **Varsayılan:** AÇIK.

### 6.10 `diagnostics` — Teşhis, Kanıt ve Benchmark (Atomized'ın vicdanı)
- **Ne:** (a) hafif frametime/1%-low/alloc-rate HUD'u; (b) >50 ms kare yakalandığında olası neden ipuçlarını loglayan Lag-Spike Logger; (c) `/atomized bench <saniye>` — sabit turlu benchmark, CSV çıktı (`.minecraft/atomized/bench/`); (d) JVM Advisor: düşük heap / kötü GC bayraklarını **tespit edip öneri gösterir** (asla kendisi değiştirmez); (e) modül durum ekranı ("şu modlar algılandı → şu modüller şu modda").
- **Kapı:** yok; her zaman güvenli.
- **Varsayılan:** HUD OFF, geri kalanı hazır.

### Yapılmayacaklar (kapsam dışı — bilinçli)
Texture animasyon optimizasyonu (Sodium zaten yapıyor), shader pipeline (Iris alanı), netty/ağ sıkıştırma (Krypton), sunucu tick mantığı (Lithium), başlangıç süresi derin cerrahisi (ModernFix), render distance'ı gizlice değiştiren hileler, mipmap/LOD hack'leri.

---

## 7. Sodium ile İlişki — "Yan yana, asla üst üste"

- Atomized, Sodium'u **algılar, tamamlar, üzerine yazmaz.** Sodium'un mixin'lediği hiçbir metoda Atomized mixin koymaz; kesişme ihtimali olan tek tek noktalar için implementasyonda Sodium'un `*.mixins.json` dosyaları taranıp `docs/COMPATIBILITY.md`'ye "dokunulmayan bölgeler haritası" çıkarılır ve CI'da **mixin audit** bunu kollar.
- `fabric.mod.json` → `"suggests": { "sodium": "*" }` (önerilir ama şart değil).
- Hard incompat: `"breaks": { "optifabric": "*" }`; `"conflicts": { "vulkanmod": "*" }` (VulkanMod renderer'ı tamamen değiştirir; Atomized yüklenirse render-bitişik modüller zaten otomatik kapanır, conflicts yalnızca kullanıcıyı uyarır).

## 8. Uyumluluk Matrisi (CI'da test edilen gerçek liste)

| Mod | İlişki | Atomized davranışı |
|---|---|---|
| Sodium | Entegrasyon | Ayar sayfası Sodium menüsünde; tüm modüller uyumlu |
| Iris (+ shader pack) | Test | Dokunma yok; shader açıkken `smart_culling` gölge-güvenli moda geçer (gölge frustum'u culling'den muaf) |
| Lithium | Uyumlu | Kesişim yok |
| FerriteCore | Uyumlu | Farklı şerit (footprint vs churn) |
| EntityCulling | Yer çakışması | `smart_culling` otomatik OFF |
| MoreCulling | Yer çakışması | `smart_culling` otomatik OFF |
| ImmediatelyFast | Kısmi | `gui_opt`/HUD-cache alt-özellikleri OFF |
| Dynamic FPS | Yer çakışması | `idle_throttle` OFF |
| ModernFix | Uyumlu | Kesişim yok |
| C2ME | Kısmi | `chunk_smooth` OFF |
| Krypton | Uyumlu | Katman farklı |
| Reese's Sodium Options / Sodium Options API | Entegrasyon | Sayfa bu API ile eklenir |
| ModMenu | Entegrasyon | Config ekranı |
| Indium, Continuity, LambDynamicLights, Zoomify, EMF/ETF, Litematica, Xaero's | Test | Smoke-test matrisinde "boot+oynanış" doğrulanır |
| OptiFabric/OptiFine | **breaks** | Yüklenmez |
| VulkanMod | conflicts | Uyarı + render modülleri OFF |

> CompatRegistry bu tabloyu **veri olarak** tutar (`compat_rules.json` kaynağında), kod değil — yeni bir çakışma keşfedilirse tek satır eklemek yeter.

---

## 9. Ayar Arayüzü — "Video Settings'te, Sodium'un altında"

**Hedef davranış:** Sodium yüklüyken kullanıcı Options → Video Settings'e girdiğinde sol sayfa listesinde Sodium'un kendi sayfalarının **altında** "⚛ Atomized" sayfası görünür; içinde modül grupları (Culling, Smoothness, Memory, Misc, Diagnostics) Sodium'un native görünümünde listelenir.

**Uygulama stratejisi (sırayla dene):**
1. **Birincil yol — Sodium Options API kütüphanesi** (`sodium-options-api`, Modrinth): tek event ile sayfa ekler — `OptionGUIConstruction.EVENT.register(pages -> pages.add(new AtomizedOptionPage()))` — ve Sodium 0.5/0.6 ile platformlar arası farkları kendisi soğurur. Kütüphane **JiJ (include)** edilir; kendi metadata'sı Sodium yokken yüklenmeye izin vermiyorsa JiJ yerine `suggests` + lazy entegrasyona düş (implementasyonda kütüphanenin `fabric.mod.json`'ını oku ve karar ver). Reese's Sodium Options yüklüyse de aynı event çalışır.
2. **İkincil yol — Sodium 0.6+ native options API** (`net.caffeinemc...` paketleri): Options API kütüphanesi belirli bir MC hedefi için yoksa doğrudan native API kullan (compileOnly, `FabricLoader.isModLoaded("sodium")` guard'ı + ayrı entegrasyon sınıfı; sınıf yalnızca Sodium varken yüklenir → `ClassNotFound` riski sıfır).
3. **Fallback — Sodium yoksa:** Atomized kendi vanilla-stili config ekranını gösterir; vanilla Video Settings ekranına `@WrapOperation` ile küçük bir "Atomized..." butonu eklenir (tek, iyi test edilmiş, isteğe bağlı kapatılabilir mixin).

**ModMenu:** `"modmenu"` entrypoint'i → ConfigScreenFactory → (Sodium varsa) Sodium ekranını doğrudan Atomized sayfası seçili açar; yoksa fallback ekran. Mod listesinde logo, yapımcı "Atom Gamer Arda A.G.A", site `atomland.xyz`, sources/issues GitHub linkleri görünür.

**Ek erişim:** `/atomized` client komutu (Fabric Client Command API) + atanmamış varsayılanlı bir keybind ("Atomized Ayarları").

---

## 10. Mod Metadata Şablonu

`src/main/resources/fabric.mod.json` (Stonecutter, MC aralığını hedef başına doldurur):

```json
{
  "schemaVersion": 1,
  "id": "atomized",
  "version": "${version}",
  "name": "Atomized",
  "description": "Complementary performance mod. Higher FPS, fewer drops, buttery-smooth frametimes — designed to run flawlessly alongside Sodium. / Sodium'un yanında kusursuz çalışan tamamlayıcı performans modu.",
  "authors": [ "Atom Gamer Arda A.G.A" ],
  "contact": {
    "homepage": "https://atomland.xyz",
    "sources": "https://github.com/<ORG>/Atomized",
    "issues": "https://github.com/<ORG>/Atomized/issues"
  },
  "license": "MIT",
  "icon": "assets/atomized/icon.png",
  "environment": "client",
  "entrypoints": {
    "client": [ "xyz.atomland.atomized.Atomized" ],
    "modmenu": [ "xyz.atomland.atomized.compat.ModMenuIntegration" ]
  },
  "mixins": [ "atomized.mixins.json" ],
  "accessWidener": "atomized.accesswidener",
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api": "*",
    "minecraft": "${minecraft_range}",
    "java": ">=21"
  },
  "suggests": { "sodium": "*", "modmenu": "*" },
  "breaks": { "optifabric": "*" },
  "conflicts": { "vulkanmod": "*" },
  "custom": { "modmenu": { "links": { "atomland.xyz": "https://atomland.xyz" } } }
}
```

---

## 11. Çoklu Sürüm Stratejisi — Stonecutter

Tek codebase, **Stonecutter** (`dev.kikugie.stonecutter`) ile sürüm-koşullu derleme; çoklu jar üretimi **chiseled** task'larıyla (`chiseledBuild`, `chiseledTest`) yapılır (Stonecutter'da çoklu sürüm derlerken chiseled task kullanmak zorunludur).

**Build hedefleri (7 hedef → 12 MC sürümü):** aynı protokol/API ailesindeki sürümler tek hedefte birleşir; **her aralığın gerçekten çalıştığı CI'da 12 sürümün hepsinde boot-test edilerek kanıtlanır** (Bölüm 13). Aile sınırlarında sorun çıkarsa hedef bölünür.

| Hedef klasör | fabric.mod.json `minecraft_range` |
|---|---|
| `1.21.1`  | `>=1.21 <=1.21.1` |
| `1.21.3`  | `>=1.21.2 <=1.21.3` |
| `1.21.4`  | `=1.21.4` |
| `1.21.5`  | `=1.21.5` |
| `1.21.8`  | `>=1.21.6 <=1.21.8` |
| `1.21.10` | `>=1.21.9 <=1.21.10` |
| `1.21.11` | `=1.21.11` *(vcsVersion / ana geliştirme hedefi — Arda'nın sunucusu bu sürümde)* |

`settings.gradle.kts` taslağı:

```kotlin
pluginManagement {
    repositories {
        mavenCentral(); gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}
plugins { id("dev.kikugie.stonecutter") version "<latest>" }

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject) {
        versions("1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11")
        vcsVersion = "1.21.11"
    }
}
```

- Sürüm farkları kodda Stonecutter yorum sözdizimiyle (`//? if >=1.21.5 { ... }`) çözülür; **mixin hedef adı değişen yerlerde** sürüm-özel mixin sınıfı + `AtomizedMixinPlugin` sürüm kapısı kullanılır.
- Hedef başına `versions/<hedef>/gradle.properties`: `minecraft_version`, `minecraft_range`, `fabric_api_version`, `sodium_version` (dev/compileOnly), `sodium_options_api_version`, `modmenu_version` — **hepsi implementasyonda Modrinth/maven'dan güncel çözülüp pinlenir.**
- Jar adı: `atomized-<modVersion>+mc<hedef>.jar` (örn. `atomized-1.0.0+mc1.21.11.jar`).

---

## 12. Repo Yapısı

```
Atomized/
├─ ATOMIZED_MASTER_PLAN.md            ← bu dosya
├─ README.md                          ← logo, rozetler, kurulum, SSS (TR+EN)
├─ LICENSE
├─ .github/workflows/
│   ├─ ci-beta.yml                    ← her commit: 300+ test → clean package → BETA release
│   └─ release-stable.yml             ← elle tetik: sürüm adıyla STABLE release
├─ settings.gradle.kts / stonecutter.gradle.kts / build.gradle.kts / gradle.properties
├─ versions/{1.21.1,1.21.3,1.21.4,1.21.5,1.21.8,1.21.10,1.21.11}/gradle.properties
├─ src/main/java/xyz/atomland/atomized/...        (Bölüm 4 ağacı)
├─ src/main/resources/
│   ├─ fabric.mod.json, atomized.mixins.json, atomized.accesswidener
│   └─ assets/atomized/{icon.png, lang/{en_us.json, tr_tr.json}}
├─ src/test/java/...                  ← fabric-loader-junit birim testleri
├─ src/clientGametest/java/...        ← Fabric client gametest senaryoları
├─ tools/bench_compare.py             ← bench CSV karşılaştırıcı (eşik kontrolü)
└─ docs/{COMPATIBILITY.md, BENCHMARKS.md, MODULES.md}
```

---

## 13. Test Stratejisi — "≥300 test" sözleşmesi

İki katman: **fabric-loader-junit** ile birim testleri (mixin/loader ortamında JUnit) + **Fabric Client Gametest API** ile gerçek istemciyi başlatan uçtan uca testler (`fabric-client-gametest` entrypoint'i, `FabricClientGameTest` arayüzü). Gametest'ler CI'da Loom **production run task** (`ClientProductionRunTask`) ile, Linux runner'da **XVFB** sanal ekran üzerinde koşar (Loom bunun için yerleşik `useXVFB` desteği sunar). Gerekirse bilinen CI ağ-senkron hatasına karşı `-Dfabric.client.gametest.disableNetworkSynchronizer=true` eklenir.

**Test bütçesi (alt sınırlar — toplam ≥ 300 garanti):**

| Paket | İçerik | Adet |
|---|---|---|
| Config | serileştirme, migrasyon, bozuk dosya kurtarma, hot-reload | 30 |
| CompatRegistry | her matris satırı için kapı kararları (mod var/yok kombinasyonları) | 40 |
| ModuleManager / PanicSwitch | yaşam döngüsü, fail-soft, çift-init koruması | 25 |
| frame_pacing | FrameClock matematiği, pacer hassasiyeti, VSync devre dışı kalma | 25 |
| smart_culling | raycast doğruluğu, cache invalidation, muafiyet kuralları (property-based dahil) | 40 |
| particle_control | bütçe/eviction sırası, beyaz liste | 25 |
| load_governor | histerezis, kademeli geri açılım | 20 |
| chunk_smooth | kuyruk sırası, oyuncu-altı önceliği | 20 |
| memory_relief & gui_opt | cache doğruluğu (yanlış cache = görsel bug ⇒ sıkı test) | 30 |
| **Mixin Audit** | her hedefte tüm mixin config'lerinin temiz uygulandığını doğrulayan parametrik test (`mixin.debug.verify`) | 7 hedef × ~20 ≈ 140 |
| Client gametest senaryoları | boot→dünya→60 tick→screenshot→temiz çıkış; ayar ekranı açma; modül toggle; bench smoke | profil başına 8 |

**Client gametest profilleri (matris):** `vanilla-only` (12 MC sürümünün TAMAMINDA — aralık iddialarının kanıtı), `+sodium`, `+sodium+iris+lithium+ferritecore+immediatelyfast+modmenu` (7 build hedefinde). Profil modları CI'da Modrinth API'den sürüme uygun olarak indirilir (Gradle `downloadCompatMods` task'ı, cache'li).

**Sayı kapısı:** `verifyTestCount` Gradle task'ı tüm JUnit XML sonuçlarını toplayıp `executed >= 300` değilse build'i FAIL eder. CI'da release job'u bu kapıya bağlıdır → "300 testten geçmeden yayın yok" kuralı mekanik olarak garanti.

---

## 14. CI/CD — GitHub Actions

**Politika (Arda'nın istediği akış birebir):**
- `main`'e atılan **her commit**: tam test hattı (≥300) → `clean` paketleme → **otomatik BETA release** (`prerelease: true`, tag `beta-<run>`, sürüm `X.Y.Z-beta.<run>+<sha7>`).
- **Elle tetiklenen** `release-stable` (sürüm adı girilerek): aynı tam hat → **STABLE release** (`prerelease: false`, tag `v<version>`).
- Test/kapı başarısızsa **release job'u hiç çalışmaz.**
- PR/yan dallar: yalnızca test, release yok.

### 14.1 `.github/workflows/ci-beta.yml` (taslak — Claude Code finalize eder)

```yaml
name: CI & Beta Release
on:
  push:
    branches: [ main ]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write

jobs:
  unit-and-audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Unit tests + mixin audit (all targets)
        run: ./gradlew chiseledTest --stacktrace
      - name: Enforce >=300 executed tests
        run: ./gradlew verifyTestCount
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: test-results, path: '**/build/test-results/**' }

  client-gametest:
    needs: unit-and-audit
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        target: ["1.21.1","1.21.3","1.21.4","1.21.5","1.21.8","1.21.10","1.21.11"]
        profile: ["vanilla", "sodium", "fullstack"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Install xvfb
        run: sudo apt-get update && sudo apt-get install -y xvfb
      - name: Download compat mods (Modrinth)
        if: matrix.profile != 'vanilla'
        run: ./gradlew :${{ matrix.target }}:downloadCompatMods -Pprofile=${{ matrix.profile }}
      - name: Run client gametests (headless, XVFB)
        run: ./gradlew :${{ matrix.target }}:runProductionClientGameTest -Pprofile=${{ matrix.profile }}
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: gametest-${{ matrix.target }}-${{ matrix.profile }}, path: 'run/**/screenshots/**' }

  range-boot-check:
    # 7 hedef jar'ın, kapsadığı 12 MC sürümünün HER BİRİNDE boot ettiğinin kanıtı
    needs: unit-and-audit
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        mc: ["1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11"]
    steps:
      - uses: actions/checkout@v4
      # Claude Code: bunu Loom prod-run İLE uygula; alternatif olarak
      # headlesshq/mc-runtime-test action'ı da kullanılabilir (xvfb + gerçek launcher boot testi).
      - name: Boot test on exact MC ${{ matrix.mc }}
        run: ./gradlew bootTest -Pmc_exact=${{ matrix.mc }}

  build-and-beta:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [ unit-and-audit, client-gametest, range-boot-check ]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Clean package (all targets)
        run: ./gradlew clean chiseledBuild -Pversion_suffix="-beta.${{ github.run_number }}+${GITHUB_SHA::7}"
      - name: Collect jars
        run: |
          mkdir -p dist
          find . -path '*/build/libs/atomized-*.jar' ! -name '*-sources.jar' ! -name '*-dev*.jar' -exec cp {} dist/ \;
      - name: Create BETA release
        env: { GH_TOKEN: '${{ secrets.GITHUB_TOKEN }}' }
        run: |
          TAG="beta-${{ github.run_number }}"
          gh release create "$TAG" dist/*.jar \
            --prerelease \
            --title "Atomized Beta #${{ github.run_number }} (${GITHUB_SHA::7})" \
            --generate-notes
```

### 14.2 `.github/workflows/release-stable.yml` (taslak)

```yaml
name: Stable Release
on:
  workflow_dispatch:
    inputs:
      version:
        description: "Stable surum (orn. 1.0.0)"
        required: true

permissions:
  contents: write

jobs:
  full-pipeline:
    uses: ./.github/workflows/ci-beta.yml   # Claude Code: test joblarini reusable workflow'a ayir
    secrets: inherit

  publish:
    needs: full-pipeline
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Clean package with explicit version
        run: ./gradlew clean chiseledBuild -Pversion_override=${{ inputs.version }}
      - name: Collect jars
        run: |
          mkdir -p dist
          find . -path '*/build/libs/atomized-*.jar' ! -name '*-sources.jar' ! -name '*-dev*.jar' -exec cp {} dist/ \;
      - name: Create STABLE release
        env: { GH_TOKEN: '${{ secrets.GITHUB_TOKEN }}' }
        run: |
          gh release create "v${{ inputs.version }}" dist/*.jar \
            --title "Atomized ${{ inputs.version }}" \
            --generate-notes
```

**CI notları (Claude Code için):**
- Test joblarını gerçek implementasyonda **reusable workflow**'a (`workflow_call`) çıkar; iki dosya aynı hattı paylaşsın, kopya olmasın.
- Gradle cache + Loom cache + Modrinth indirme cache'i (`actions/cache`) ile pipeline süresini düşür.
- `main` dalına branch protection: "CI yeşil olmadan merge yok" (Arda repo ayarından açar; README'ye not düş).
- İleride Modrinth/CurseForge yayını için `mod-publish-plugin` entegrasyonu hazır bırakılır (kapalı, TODO).

---

## 15. Performans Doğrulama Protokolü ("gerçekten hızlandırıyor" kanıtı)

CI, FPS ölçemez (GPU yok) → CI **doğruluk + ≥300 test** kapısıdır; **performans kanıtı** her stable öncesi şu protokole göre yerelde alınır ve `docs/BENCHMARKS.md`'ye işlenir:

1. **Sabit sahneler** (repo'da seed + setup komut dosyaları): S1 boş süperflat (regresyon kontrolü), S2 200-mob hayvan/zombi farmı, S3 parçacık fırtınası (komutla), S4 büyük köy + 32 chunk gezinti turu (otomatik kamera yolu — gametest API ile script'lenir), S5 elytra ile hızlı chunk yükleme.
2. **Konfigürasyonlar:** Vanilla / Vanilla+Atomized / Sodium / Sodium+Atomized / Sodium+(Lithium+FerriteCore+IF)+Atomized.
3. **Ölçüm:** `/atomized bench 60` → CSV (avg, %1 low, %0.1 low, frametime stddev, alloc MB/s). 3'er tekrar, medyan alınır. İsteğe bağlı derin profil: Loom production run'ın **tracy-capture** entegrasyonu.
4. **Karar:** `tools/bench_compare.py` CSV'leri karşılaştırır, Bölüm 2 eşiklerini geçemeyen sürüm stable olamaz.
5. JMH mikro-benchmark'ları (pacer matematiği, raycast, kuyruk) CI'da **bilgilendirici** koşar; >%20 sapma uyarı verir.

---

## 16. Sürümleme ve Yayın Politikası

- SemVer: `MAJOR.MINOR.PATCH`. Beta otomatik: `<sonrakiSürüm>-beta.<run>+<sha7>`. Stable: elle girilen sürüm.
- Her stable: değişiklik notları (TR+EN özet), benchmark tablosu linki, desteklenen MC aralığı tablosu.
- `gradle.properties` → `mod_version=1.0.0` taban; CI suffix/override ile oynar.
- 1.21.11 sonrası MC sürümleri: Atomized 2.x dalı (bu plan kapsamı dışı).

---

## 17. Kilometre Taşları (Claude Code'un sıralı görev listesi)

**M0 — İskelet (DoD: `chiseledBuild` 7 hedefte boş modla yeşil)**
Stonecutter + Loom + mojmap kurulumu; 7 hedef; `fabric.mod.json`, mixins.json, boş `Atomized` entrypoint; logo indirilip `icon.png` olarak commit; lang dosyaları; README iskeleti; lisans.

**M1 — Çekirdek (DoD: birim testleriyle ≥95 test yeşil)**
ModuleManager, AtomizedModule, CompatRegistry (+`compat_rules.json`), PanicSwitch, Config sistemi, AtomizedMixinPlugin, `/atomized` komutu.

**M2 — GUI (DoD: Sodium'lu ve Sodium'suz ekranlar gametest'te açılıyor)**
Sodium Options API entegrasyonu (Bölüm 9 sırasıyla), ModMenu, fallback ekran, keybind.

**M3 — Modül Dalga 1 (DoD: her modül kendi test paketiyle; bench'te ölçülebilir kazanç)**
`frame_pacing`, `idle_throttle`, `particle_control`, `load_governor`, `diagnostics` (bench dahil).

**M4 — Modül Dalga 2 (DoD: aynı)**
`smart_culling` (en kapsamlı — gölge/Iris güvenliği dahil), `memory_relief`, `gui_opt`, `chunk_smooth`, `sp_boost`.

**M5 — Uyumluluk Sertleştirme (DoD: Bölüm 8 matrisinin tamamı CI'da yeşil; COMPATIBILITY.md dolu)**
downloadCompatMods, gametest profilleri, mixin audit'in Sodium-bölge haritasıyla genişletilmesi, 12-sürüm range-boot-check.

**M6 — CI/CD + Yayın (DoD: main'e push → otomatik beta; dispatch → stable; verifyTestCount ≥300 zorunlu)**
İki workflow + reusable pipeline, cache'ler, ilk `1.0.0` stable için Bölüm 15 benchmark raporu.

---

## 18. Risk Kaydı / Kesin Yasaklar

| Risk | Önlem |
|---|---|
| Mixin çakışması | Altın Kurallar §1; mixin audit; MixinExtras-only |
| Görsel doğruluk bozulması (yanlış culling/cache) | Muafiyet kuralları, screenshot'lı gametest'ler, "şüphede render et" ilkesi |
| Anti-cheat tetikleme | Sunucuya giden hiçbir paket/oyun mantığı değişmez (§6) |
| Sürüm ailesi varsayımının kırılması | 12-sürüm range-boot-check; kırılırsa hedef bölünür |
| Sahte "optimizasyon" (placebo) | Bölüm 15 protokolü olmadan hiçbir kazanç iddiası README'ye yazılamaz |
| Bağımlılık sürüm çürümesi | Tüm sürümler pinli; Renovate/Dependabot açılır |
| Logo linkinin ölmesi | Görsel repoya commit'li; runtime'da harici istek yok |

---

## 19. Son Kontrol Listesi (1.0.0 yayın kapısı)

- [ ] 7 jar, 12 MC sürümünde boot-test yeşil
- [ ] ≥300 test, mixin audit, tüm gametest profilleri yeşil
- [ ] Bölüm 2 performans eşikleri BENCHMARKS.md'de kanıtlı
- [ ] Sodium menüsünde Atomized sayfası + ModMenu + fallback çalışıyor
- [ ] Uyumluluk matrisi davranışları (otomatik kapanmalar) elle de doğrulandı
- [ ] README (TR+EN), MODULES.md, COMPATIBILITY.md tamam
- [ ] Beta hattı: rastgele bir commit → otomatik prerelease oluştu
- [ ] Stable hattı: `release-stable` → `v1.0.0` oluştu

---

## Ek: Claude Code'a verilecek ilk komut (Arda kopyala-yapıştır yapabilir)

> "Read ATOMIZED_MASTER_PLAN.md in the repo root. It is the single source of truth. Start with milestone M0 and proceed strictly in order; do not start a milestone before the previous one's DoD is fully met. Resolve all dependency versions from their real upstream sources before pinning. Follow the Golden Rules in section 5 without exception. Work test-first."
