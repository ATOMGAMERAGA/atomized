import json, zipfile, glob, re, os
from collections import defaultdict

home = os.path.expanduser('~')
jars = glob.glob(home + '/.gradle/caches/modules-2/files-2.1/maven.modrinth/sodium/*/*/sodium-*.jar')
jars = [j for j in jars if 'sources' not in j]
configs = ["sodium-common.mixins.json","sodium-fabric.mixins.json","sodium-frapi.mixins.json","sodium.mixins.json"]

per_ver = {}
for jar in sorted(jars):
    m = re.search(r'(mc1\.21\.\d+-[\d.]+)', jar)
    label = m.group(1) if m else jar
    mixins = set()
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for cfg in configs:
            if cfg not in names: continue
            d = json.loads(z.read(cfg))
            for k in ("mixins","client","server"):
                mixins |= set(d.get(k,[]))
    per_ver[label] = mixins

print("Versions found:", len(per_ver))
latest = sorted(per_ver.keys())[-1]
print("LATEST:", latest, "->", len(per_ver[latest]), "mixin classes\n")
groups = defaultdict(list)
for mx in sorted(per_ver[latest]):
    grp = ".".join(mx.split(".")[:-1]) or "(root)"
    groups[grp].append(mx.split(".")[-1])
for g in sorted(groups):
    print(f"[{g}]\n  " + ", ".join(groups[g]))

# Union of touched top-level areas across ALL versions (for the do-not-touch map)
areas = defaultdict(set)
for ver, mxs in per_ver.items():
    for mx in mxs:
        parts = mx.split(".")
        # area = first 2 sub-packages after 'mixin'
        area = ".".join(parts[:3])
        areas[area].add(ver)
