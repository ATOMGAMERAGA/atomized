import json, zipfile, glob, os
home=os.path.expanduser('~')
jars=[j for j in glob.glob(home+'/.gradle/caches/modules-2/files-2.1/maven.modrinth/sodium/*/*/sodium-*.jar') if 'sources' not in j]
configs=['sodium-common.mixins.json','sodium-fabric.mixins.json','sodium-frapi.mixins.json','sodium.mixins.json']
touched=set()
for jar in jars:
    with zipfile.ZipFile(jar) as z:
        for cfg in configs:
            if cfg in z.namelist():
                d=json.loads(z.read(cfg))
                for m in d.get('client',[])+d.get('mixins',[])+d.get('server',[]):
                    s=m.split('.')[-1].split('$')[0]
                    for suf in ('Mixin','Accessor','Invoker'):
                        if s.endswith(suf): s=s[:-len(suf)]; break
                    if s: touched.add(s)

# Curated allowlist: classes Sodium hooks only at lifecycle/event/data points (not render
# rewrites), where a chainable MixinExtras @Inject/@WrapOperation provably coexists.
# Reviewed against Sodium source subpackages (core lifecycle, gui hooks, world.map, options).
injectable_overlap = sorted([
    "Minecraft",              # core lifecycle + window-minimized tweak; runTick inject coexists
    "ClientPacketListener",   # world.map; chunk-packet handlers are normal methods
    "ClientChunkCache",       # world.map
    "EntityRenderDispatcher", # only shadow feature hooked; shouldRender wrap coexists
    "Gui",                    # options overlay hook
    "DebugScreenOverlay",     # debug hud hook
    "OptionsScreen",          # settings button hook
    "LevelLoadStatusManager", # load-status hook
    "LevelLoadingScreen",     # loading-screen hook
])
forbidden = sorted(touched - set(injectable_overlap))

out={
  "_comment":("Generated from Sodium mixin configs across MC 1.21.1-1.21.11 (tools/). "
              "Atomized must NEVER @Mixin a 'forbidden' class (Sodium-owned render/internal code). "
              "It MAY target an 'injectable_overlap' class, but ONLY with chainable MixinExtras "
              "injectors (never @Overwrite/@Redirect). Any class absent from both lists is untouched "
              "by Sodium and free to use with chainable injectors."),
  "sodium_versions_scanned": 7,
  "forbidden": forbidden,
  "injectable_overlap": injectable_overlap
}
with open('/home/user/Atomized/src/main/resources/atomized_sodium_regions.json','w') as f:
    json.dump(out,f,indent=2)
print("forbidden:",len(forbidden)," injectable_overlap:",len(injectable_overlap))
for c in ['Minecraft','EntityRenderDispatcher','ParticleEngine','ClientPacketListener','LevelRenderer','GameRenderer','Screen']:
    w='forbidden' if c in forbidden else ('injectable_overlap' if c in injectable_overlap else 'CLEAN')
    print(f'  {c:24} {w}')
