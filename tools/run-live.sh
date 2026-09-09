#!/bin/sh
# R1 live gate (was B3): Forge 1.7.10-1614 server run proving PackWire.bind
# (real Block resolve) plus world-tick apply on a real world, then comparing
# the world against the pure decision union (tools/live).
#
# Manual gate (needs network once + Java 8 + 1614 SRG mappings); opt-in from
# tools/check.sh via LIVE=1, never blocking by default. Never silent: any
# mismatch fails loudly, never defaulted.
#
# Env (no machine paths hardcoded):
#   B3_DIR     work dir (default ${TMPDIR:-/tmp}/matou-b3-live)
#   JAVA8_HOME Java 8 home (default /usr/lib/jvm/java-8-openjdk)
#   SRG_MCP    path to srg-mcp.srg for 1.7.10-1614 (default: ForgeGradle
#              cache under $HOME; override for userdev copies)
#   FORGE_URL  installer URL (default Maven 1614 installer)
#   BOOT_SECS  server run time (default 150; short runs fail coverage loudly)
#   B3_OFFLINE=1  never download (fail loudly if cache files missing)
#
# R2 release assembly: BUILD_ONLY=1 VERSION=x.y.z assembles dist/ (versioned
# jars + content + packs.cfg.example + SHA256SUMS) and exits before booting
# the server. Release demands strict X.Y.Z, a clean tree in all 4 code repos,
# and @Mod version == VERSION; anything else fails loudly, never defaulted.
# SOURCE_DATE_EPOCH pins jar entry timestamps (default: bridge HEAD commit
# time); with a pinned toolchain (tools/live/Dockerfile) the same commit
# always yields the same bytes.
#
# Reproducibility pins (R1): installer / universal / SRG sha1 below. Any
# upstream drift fails loudly instead of running against unknown bytes.
set -eu
cd "$(dirname "$0")/.."
B3_DIR="${B3_DIR:-${TMPDIR:-/tmp}/matou-b3-live}"
JAVA8_HOME="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk}"
FORGE_URL="${FORGE_URL:-https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-installer.jar}"
BOOT_SECS="${BOOT_SECS:-150}"
# R1 pins: measured 2026-09-09 from Maven installer + installed universal +
# ForgeGradle 1614 srg-mcp.srg + provisioned ASM 5.0.3 (identical sha1 across
# 3 independent server caches). Drift = loud failure, never silent upgrade.
INSTALLER_SHA1="fccafccf8ad4ce6d9f008e786b48ff53172bf9de"
UNIVERSAL_SHA1="25fd97f72beca728112256938e03e8105b1b78cc"
SRG_MCP_SHA1="1a97ef852abe78595d8f08a7975288448f244254"
ASM_PIN="asm-all-5.0.3.jar"
ASM_SHA1="4333508b8dd8ee72aa4e39afa713b3a74579b773"
# SRG auto-discover (R1): ForgeGradle cache first, $SRG_MCP override wins.
# No machine paths hardcoded: the default derives from $HOME.
if [ -z "${SRG_MCP:-}" ]; then
  SRG_MCP="$HOME/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/srgs/srg-mcp.srg"
fi
[ -f "$SRG_MCP" ] || { echo "FAIL b3-live : SRG_MCP=<$SRG_MCP> missing (set SRG_MCP or run a ForgeGradle 1614 setup once)"; exit 1; }
echo "$SRG_MCP_SHA1  $SRG_MCP" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL b3-live : SRG_MCP sha1 drift (want $SRG_MCP_SHA1)"; exit 1; }
J8="$JAVA8_HOME/bin"
[ -x "$J8/java" ] || { echo "FAIL b3-live : no Java 8 at <$JAVA8_HOME>"; exit 1; }
[ -d ../spi/java/src ] || { echo "FAIL b3-live : spi sibling absent"; exit 1; }
[ -d ../example1/java/src ] || { echo "FAIL b3-live : example1 sibling absent"; exit 1; }
[ -d ../minimap/java/src ] || { echo "FAIL b3-live : minimap sibling absent"; exit 1; }
command -v python3 >/dev/null || { echo "FAIL b3-live : python3 required (anvil verify)"; exit 1; }
# R2 versioning: VERSION stamps manifests + mcmod.info. Dev live runs take an
# explicit non-release default; release assembly demands strict X.Y.Z.
VERSION="${VERSION:-0.0-dev}"
if [ "${BUILD_ONLY:-}" = "1" ]; then
  printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
    || { echo "FAIL r2-release : VERSION=<$VERSION> not X.Y.Z (want e.g. 1.0.0)"; exit 1; }
  for r in . ../spi ../example1 ../minimap; do
    git -C "$r" diff --quiet && git -C "$r" diff --cached --quiet \
      || { echo "FAIL r2-release : dirty tree in <$r> (release from clean checkouts only)"; exit 1; }
  done
  grep -q "version = \"$VERSION\"" forge/src/fr/iamacat/bridge/forge/MatouBridgeMod.java \
    || { echo "FAIL r2-release : @Mod version != VERSION=<$VERSION> (bump source first)"; exit 1; }
fi

# 1. Pin every stubbed vanilla member to the exact SRG used for reobf.
#    A stub the SRG does not know is a loud failure, never a silent default.
pin_method() {
  grep -q "^MD: [^ ]* [^ ]* $1 $2\$" "$SRG_MCP" \
    || { echo "FAIL b3-live : stub member unpinned <$1 $2>"; exit 1; }
}
pin_field() {
  grep -q "^FD: [^ ]* $1\$" "$SRG_MCP" \
    || { echo "FAIL b3-live : stub field unpinned <$1>"; exit 1; }
}
pin_method "net/minecraft/block/Block/getBlockFromName" "(Ljava/lang/String;)Lnet/minecraft/block/Block;"
pin_method "net/minecraft/world/World/setBlock" "(IIILnet/minecraft/block/Block;)Z"
pin_field "net/minecraft/world/World/provider"
pin_field "net/minecraft/world/WorldProvider/dimensionId"
echo "ok b3-live : stubs pinned to SRG"

# 2. Provision the 1614 server once (idempotent, checksum-verified).
#    B3_OFFLINE=1 never touches the network: missing cache fails loudly.
mkdir -p "$B3_DIR"
SERV="$B3_DIR/server"
mkdir -p "$SERV"
if [ ! -f "$B3_DIR/forge-installer.jar" ]; then
  if [ "${B3_OFFLINE:-}" = "1" ]; then
    echo "FAIL b3-live : offline and installer absent ($B3_DIR/forge-installer.jar)"
    exit 1
  fi
  curl -sL -o "$B3_DIR/forge-installer.jar" "$FORGE_URL" \
    || { echo "FAIL b3-live : installer download"; exit 1; }
fi
echo "$INSTALLER_SHA1  $B3_DIR/forge-installer.jar" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL b3-live : installer sha1 drift (want $INSTALLER_SHA1)"; exit 1; }
UNI="$SERV/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar"
if [ ! -f "$UNI" ]; then
  (cd "$SERV" && "$J8/java" -jar "$B3_DIR/forge-installer.jar" --installServer >/dev/null 2>&1) \
    || { echo "FAIL b3-live : --installServer"; exit 1; }
fi
echo "$UNIVERSAL_SHA1  $UNI" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL b3-live : universal sha1 drift (want $UNIVERSAL_SHA1)"; exit 1; }
ASM=$(find "$SERV/libraries/org/ow2/asm" -name "$ASM_PIN" | head -n 1)
[ -n "$ASM" ] || { echo "FAIL b3-live : ASM $ASM_PIN missing from server libs"; exit 1; }
echo "$ASM_SHA1  $ASM" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL b3-live : ASM sha1 drift (want $ASM_SHA1)"; exit 1; }
echo "ok b3-live : server provisioned (pins verified)"

# 2b. Pin every stubbed Forge member against the provisioned 1614 universal.
#     Forge classes are never obfuscated, so names are final — presence is
#     the pin. (Vanilla-typed Forge members reference obfuscated classes in
#     the universal, which is why forge/ compiles against stubs, not it.)
pin_uni() {
  "$J8/javap" -p -cp "$UNI" "$1" 2>/dev/null | grep -q "$2" \
    || { echo "FAIL b3-live : universal pin unmet <$1 :: $2>"; exit 1; }
}
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$WorldTickEvent' 'world'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent' 'side'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent' 'phase'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$Phase' 'END'
pin_uni 'cpw.mods.fml.relauncher.Side' 'SERVER'
pin_uni 'cpw.mods.fml.common.Mod' 'modid()'
pin_uni 'cpw.mods.fml.common.FMLCommonHandler' 'instance()'
pin_uni 'cpw.mods.fml.common.eventhandler.EventBus' 'register('
echo "ok b3-live : forge stubs pinned to universal"

# 3. Build all mod jars with Java 8. forge/ compiles against the pinned
#    stubs (vanilla shape + Forge shape); the live run is the semantic arbiter.
#    R2: jar entries are sorted with timestamps clamped to EPOCH (same
#    commit + same toolchain == same bytes, see normjar), manifests carry
#    VERSION, the bridge jar embeds mcmod.info.
#    These are the exact bytes the live run proves AND the release ships.
BLD="$B3_DIR/build"
rm -rf "$BLD" \
  || { echo "FAIL b3-live : cannot clear <$BLD> (root-owned docker leftovers? point B3_DIR at a user-owned dir)"; exit 1; }
mkdir -p "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/bridge" "$BLD/forge" "$BLD/jars"
"$J8/javac" -source 8 -target 8 -nowarn -d "$BLD/spi" $(find ../spi/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi" -d "$BLD/ex1" $(find ../example1/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi" -d "$BLD/mini" $(find ../minimap/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD/bridge" $(find java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi:$BLD/ex1:$BLD/bridge" -d "$BLD/forge" $(find tools/live/stub forge/src -name '*.java')
EPOCH="${SOURCE_DATE_EPOCH:-$(git log -1 --format=%ct)}"
printf 'Manifest-Version: 1.0\nImplementation-Version: %s\n' "$VERSION" > "$BLD/MANIFEST.MF"
cat > "$BLD/mcmod.info" <<EOF
[{"modid": "matoubridge", "name": "MatouBridge", "description": "SPI bridge for Minecraft 1.7.10 (reobfuscated SRG).", "version": "$VERSION", "mcversion": "1.7.10", "authorList": ["matou-dev"], "url": "https://github.com/matou-dev/bridge-1710"}]
EOF
find "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/bridge" "$BLD/forge" "$BLD/MANIFEST.MF" "$BLD/mcmod.info" -exec touch -h -d "@$EPOCH" {} +
# mkjar: sorted entries, pinned mtimes, VERSION manifest. File lists stay
# explicit because jar -C . walks in readdir order (not reproducible).
# normjar then clamps every zip entry timestamp: the JDK 8 jar tool stamps
# META-INF entries with the wall clock (verified by diff), and Reobf does
# the same for its output. python3 is already a hard dependency (anvil).
# Scope: same commit + same toolchain == same bytes (zlib/JDK may vary
# across machines; use tools/live/Dockerfile to pin the toolchain).
normjar() {
  python3 - "$1" "$EPOCH" <<'EOF'
import sys, zipfile, datetime
path, epoch = sys.argv[1], int(sys.argv[2])
dt = datetime.datetime.utcfromtimestamp(epoch).timetuple()[:6]
zin = zipfile.ZipFile(path)
items = [(i, zin.read(i.filename)) for i in zin.infolist()]
zin.close()
zout = zipfile.ZipFile(path + ".norm", "w", zipfile.ZIP_DEFLATED)
for info, data in items:
    info.date_time = dt
    info.create_system = 0
    zout.writestr(info, data)
zout.close()
EOF
  mv "$1.norm" "$1"
}
mkjar() {
  out="$1"; stage="$2"
  files=$(cd "$stage" && find . -type f | LC_ALL=C sort)
  # Controlled tree, no spaces in class paths: word-splitting is intended.
  (cd "$stage" && "$J8/jar" cfm "$out" "$BLD/MANIFEST.MF" $files)
  normjar "$out"
}
mkjar "$BLD/jars/matou-spi.jar" "$BLD/spi"
mkjar "$BLD/jars/matou-example1.jar" "$BLD/ex1"
mkjar "$BLD/jars/matou-minimap.jar" "$BLD/mini"
rm -rf "$BLD/bridgemod" && mkdir -p "$BLD/bridgemod"
cp -r "$BLD/bridge/"* "$BLD/bridgemod/" && cp -r "$BLD/forge/"* "$BLD/bridgemod/"
# Stubs are compile-only: they must never ship (a fake Block on the
# runtime classpath would shadow vanilla). Refuse loudly if leaked.
rm -rf "$BLD/bridgemod/net" "$BLD/bridgemod/cpw"
if [ -e "$BLD/bridgemod/net" ] || [ -e "$BLD/bridgemod/cpw" ]; then
  echo "FAIL b3-live : stub leak into mod jar"
  exit 1
fi
cp "$BLD/mcmod.info" "$BLD/bridgemod/mcmod.info"
touch -h -d "@$EPOCH" "$BLD/bridgemod/mcmod.info"
mkjar "$BLD/jars/matoubridge.jar" "$BLD/bridgemod"
echo "ok b3-live : jars built (VERSION=$VERSION)"

# 4. Reobfuscate MCP-named refs to SRG (ForgeGradle reobf equivalent:
#    runtime vanilla only declares SRG names, so un-reobfed jars die with
#    NoSuchMethodError — found live in B3, never again silently).
"$J8/javac" -cp "$ASM" -d "$BLD" tools/live/Reobf.java
"$J8/java" -cp "$BLD:$ASM" Reobf "$SRG_MCP" "$BLD/jars/matoubridge.jar" "$BLD/jars/matoubridge-reobf.jar"
normjar "$BLD/jars/matoubridge-reobf.jar"
echo "ok b3-live : bridge reobfuscated"

# Dual-runtime contract (Java 8 vanilla + modern JVM via lwjgl3ify):
# shipped bytes stay major 52 with no module-info and no multi-release
# entries — v52 loads on 8 and 21 alike. Anything newer fails loudly here,
# on both the live and the release path, never silently.
python3 - "$BLD/jars" <<'EOF'
import sys, zipfile, struct
jars = ["matou-spi.jar", "matou-example1.jar", "matou-minimap.jar",
        "matoubridge-reobf.jar"]
bad = []
for j in jars:
    zf = zipfile.ZipFile("%s/%s" % (sys.argv[1], j))
    for n in zf.namelist():
        if n == "module-info.class" or n.startswith("META-INF/versions/"):
            bad.append("%s!%s (multi-release)" % (j, n))
        elif n.endswith(".class"):
            major = struct.unpack(">H", zf.read(n)[6:8])[0]
            if major != 52:
                bad.append("%s!%s (major %d, want 52)" % (j, n, major))
if bad:
    print("FAIL b3-live : java-52 contract broken:")
    print("\n".join("  " + b for b in bad))
    sys.exit(1)
print("ok b3-live : java 52 contract (4 jars, no multi-release)")
EOF

# R2 release assembly: versioned server drop, then exit before booting.
# The MCP-named bridge jar never ships (only the reobf one is copied).
if [ "${BUILD_ONLY:-}" = "1" ]; then
  rm -rf dist && mkdir -p dist/matou-content
  cp "$BLD/jars/matou-spi.jar" "dist/matou-spi-$VERSION.jar"
  cp "$BLD/jars/matou-example1.jar" "dist/matou-example1-$VERSION.jar"
  cp "$BLD/jars/matou-minimap.jar" "dist/matou-minimap-$VERSION.jar"
  cp "$BLD/jars/matoubridge-reobf.jar" "dist/matoubridge-$VERSION.jar"
  cp ../example1/content/owned.matou ../example1/content/additive.matou ../example1/content/structure.matou dist/matou-content/
  printf '# Copy to <server>/config/matoubridge/packs.cfg and replace <SERVER>.\nfr.iamacat.example1.ExamplePack 64 minecraft:stone ownedFile=<SERVER>/matou-content/owned.matou scatterFile=<SERVER>/matou-content/additive.matou\n# Optional structure job (x,y,z:block cells land at their own y; every palette block must resolve vanilla-side):\n# fr.iamacat.example1.ExamplePack 64 minecraft:stone ownedFile=<SERVER>/matou-content/owned.matou scatterFile=<SERVER>/matou-content/additive.matou structureFile=<SERVER>/matou-content/structure.matou\n' > dist/packs.cfg.example
  (cd dist && sha256sum "matou-spi-$VERSION.jar" "matou-example1-$VERSION.jar" "matou-minimap-$VERSION.jar" "matoubridge-$VERSION.jar" matou-content/owned.matou matou-content/additive.matou matou-content/structure.matou packs.cfg.example > SHA256SUMS.txt)
  (cd dist && sha256sum -c SHA256SUMS.txt)
  echo "ok r2-release : dist/ assembled (VERSION=$VERSION)"
  exit 0
fi

# 5. Deploy mods + content + packs.cfg, boot the server.
mkdir -p "$SERV/mods" "$SERV/config/matoubridge"
rm -f "$SERV/mods/"*.jar
cp "$BLD/jars/matou-spi.jar" "$BLD/jars/matou-example1.jar" "$BLD/jars/matoubridge-reobf.jar" "$SERV/mods/"
mv "$SERV/mods/matoubridge-reobf.jar" "$SERV/mods/matoubridge.jar"
rm -rf "$SERV/matou-content" && cp -r ../example1/content "$SERV/matou-content"
printf 'fr.iamacat.example1.ExamplePack 64 minecraft:stone ownedFile=%s/matou-content/owned.matou scatterFile=%s/matou-content/additive.matou\n' "$SERV" "$SERV" > "$SERV/config/matoubridge/packs.cfg"
echo "eula=true" > "$SERV/eula.txt"
printf 'online-mode=false\nlevel-type=FLAT\ngamemode=1\ndifficulty=0\nmotd=B3 live proof\nmax-tick-time=-1\n' > "$SERV/server.properties"
rm -rf "$SERV/world" "$SERV/logs"
set +e
(cd "$SERV" && timeout "$BOOT_SECS" "$J8/java" -Xmx1G -jar "$UNI" nogui > boot-b3.log 2>&1)
code=$?
set -e
[ "$code" -eq 124 ] || { echo "FAIL b3-live : server exited early (code $code, see $SERV/boot-b3.log)"; exit 1; }
echo "ok b3-live : server ran ($BOOT_SECS s)"

# 6. Fail loudly on any runtime refusal or linkage error.
if grep -a -q "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|Encountered an unexpected exception" "$SERV/boot-b3.log"; then
  echo "FAIL b3-live : runtime refusal (see $SERV/boot-b3.log)"
  grep -a -m5 "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|Caused by" "$SERV/boot-b3.log"
  exit 1
fi
grep -a -q "matoubridge" "$SERV/boot-b3.log" \
  || { echo "FAIL b3-live : mod never loaded"; exit 1; }
echo "ok b3-live : bind clean, ticks clean"

# 7. Positive proof: world blocks at y=64 in chunk 0,0 must equal the pure
#    decision union — stone only, nothing foreign, nothing missing.
"$J8/javac" -cp "$BLD/spi:$BLD/ex1:$BLD/bridge" -d "$BLD" tools/live/CellUnion.java
"$J8/java" -cp "$BLD:$BLD/spi:$BLD/ex1:$BLD/bridge" CellUnion \
  "$SERV/matou-content/owned.matou" "$SERV/matou-content/additive.matou" 4000 "$BLD/union.txt"
python3 tools/live/anvil.py "$SERV/world/region/r.0.0.mca" 0 0 64 > "$BLD/world64.txt"
python3 - "$BLD/union.txt" "$BLD/world64.txt" <<'EOF'
import sys
u = {l.split()[0] for l in open(sys.argv[1])}
rows = [l.split() for l in open(sys.argv[2])]
w = {c: i for c, i in rows}
if not w:
    print("FAIL b3-live : world empty at y=64 (no tick applied?)")
    sys.exit(1)
if set(w.values()) != {"1"}:
    print("FAIL b3-live : foreign block ids %s" % sorted(set(w.values())))
    sys.exit(1)
if set(w) - u:
    print("FAIL b3-live : world cells outside pure union %s" % sorted(set(w) - u)[:5])
    sys.exit(1)
if u - set(w):
    print("FAIL b3-live : pure cells missing from world (%d)" % len(u - set(w)))
    sys.exit(1)
print("ok b3-live : world == pure union (%d cells, stone only)" % len(w))
EOF
