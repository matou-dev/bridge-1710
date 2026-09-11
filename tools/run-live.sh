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
#   B3_DIR     work dir (default ${TMPDIR:-/tmp}/matou-b3-live; non-owned
#              leftovers refused loudly by the preflight — clean or fresh dir)
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
# Shared harness steps (hub SSOT, thin version wrapper — hub
# decisions/LIVE_SHELL_COMMON.md): sibling-absent fails loud, same shim
# discipline as tools/run-client.sh.
[ -f ../hub/tools/live-common.sh ] \
  || { echo "FAIL b3-live : hub sibling absent (clone hub next to bridge-1710 — live steps source ../hub/tools/live-common.sh)"; exit 1; }
# shellcheck disable=SC1091
. ../hub/tools/live-common.sh
live_init "b3-live"
# Era-bound adapters: live-common.sh owns the mechanics; these bind the
# caller-owned map/jars so every pin/jar call site below stays byte-identical.
pin_method() { live_pin_method "$SRG_MCP" "$@"; }
pin_field() { live_pin_field "$SRG_MCP" "$@"; }
pin_uni() { live_pin_uni "$J8" "$UNI" "$@"; }
mkjar() { live_mkjar "$1" "$2" "$BLD/MANIFEST.MF" "$J8/jar" "$EPOCH"; }
normjar() { live_normjar "$1" "$EPOCH"; }
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
  grep -q "version = \"$VERSION\"" forge/src/fr/iamacat/bridge/forge/Example1Mod.java \
    || { echo "FAIL r2-release : example1 @Mod version != VERSION=<$VERSION> (bump source first, both @Mods ride together)"; exit 1; }
fi

# 1. Pin every stubbed vanilla member to the exact SRG used for reobf.
#    A stub the SRG does not know is a loud failure, never a silent default.
pin_method "net/minecraft/block/Block/getBlockFromName" "(Ljava/lang/String;)Lnet/minecraft/block/Block;"
pin_method "net/minecraft/block/Block/setBlockName" "(Ljava/lang/String;)Lnet/minecraft/block/Block;"
pin_method "net/minecraft/block/Block/setHardness" "(F)Lnet/minecraft/block/Block;"
pin_method "net/minecraft/block/Block/isOpaqueCube" "()Z"
pin_method "net/minecraft/block/Block/getIdFromBlock" "(Lnet/minecraft/block/Block;)I"
pin_field "net/minecraft/block/material/Material/rock"
pin_field "net/minecraft/block/Block/opaque"
pin_method "net/minecraft/world/World/spawnEntityInWorld" "(Lnet/minecraft/entity/Entity;)Z"
pin_method "net/minecraft/entity/item/EntityItem/getEntityItem" "()Lnet/minecraft/item/ItemStack;"
pin_method "net/minecraft/item/ItemStack/getItem" "()Lnet/minecraft/item/Item;"
pin_method "net/minecraft/item/Item/setMaxStackSize" "(I)Lnet/minecraft/item/Item;"
pin_method "net/minecraft/item/Item/setUnlocalizedName" "(Ljava/lang/String;)Lnet/minecraft/item/Item;"
pin_method "net/minecraft/item/Item/getIdFromItem" "(Lnet/minecraft/item/Item;)I"
pin_field "net/minecraft/init/Items/diamond"
pin_field "net/minecraft/entity/Entity/worldObj"
pin_field "net/minecraft/entity/Entity/posX"
pin_field "net/minecraft/entity/Entity/posY"
pin_field "net/minecraft/entity/Entity/posZ"
pin_method "net/minecraft/world/World/setBlock" "(IIILnet/minecraft/block/Block;)Z"
pin_method "net/minecraft/entity/Entity/getEntityId" "()I"
pin_method "net/minecraft/entity/Entity/setPositionAndRotation" "(DDDFF)V"
pin_method "net/minecraft/entity/EntityLivingBase/getEntityAttribute" "(Lnet/minecraft/entity/ai/attributes/IAttribute;)Lnet/minecraft/entity/ai/attributes/IAttributeInstance;"
pin_method "net/minecraft/entity/EntityLivingBase/getMaxHealth" "()F"
pin_method "net/minecraft/entity/EntityLivingBase/setHealth" "(F)V"
pin_method "net/minecraft/entity/ai/attributes/IAttributeInstance/setBaseValue" "(D)V"
pin_field "net/minecraft/entity/SharedMonsterAttributes/maxHealth"
pin_field "net/minecraft/world/World/loadedEntityList"
pin_field "net/minecraft/entity/Entity/isDead"
pin_field "net/minecraft/world/World/provider"
pin_field "net/minecraft/world/WorldProvider/dimensionId"
pin_field "net/minecraft/world/World/isRemote"
# Renderer tranche (hub decisions/MATOU_MODEL.md, ported from 1122): every
# net/minecraft/* member the client-only InstancedMeshRenderer touches.
# Anchors are srg-mcp.srg-derived above (same grep discipline — never
# recalled): theWorld is the WorldClient-typed field_71441_e,
# renderViewEntity the field_71451_h (a field on 1614, the 1122 method
# does not exist here), getMinecraft func_71410_x.
pin_method "net/minecraft/client/Minecraft/getMinecraft" "()Lnet/minecraft/client/Minecraft;"
pin_field "net/minecraft/client/Minecraft/theWorld"
pin_field "net/minecraft/client/Minecraft/renderViewEntity"
pin_field "net/minecraft/entity/Entity/lastTickPosX"
pin_field "net/minecraft/entity/Entity/lastTickPosY"
pin_field "net/minecraft/entity/Entity/lastTickPosZ"
pin_field "net/minecraft/entity/Entity/rotationYaw"
pin_field "net/minecraft/entity/Entity/rotationPitch"
# Combat tranche (hub decisions/VIRTUAL_HITBOXES.md, server weakspot hook
# — ported from the 1122 lead): the attacker eye/look surface plus the
# look components. 1614-native notes: DamageSource.getEntity is the
# 1.7.10 MCP name for the 1122 getTrueSource (same searge func_76346_g),
# and the look type is Vec3 with xCoord/yCoord/zCoord (no Vec3d here) —
# same grep discipline as every row above.
pin_method "net/minecraft/entity/Entity/getLookVec" "()Lnet/minecraft/util/Vec3;"
pin_method "net/minecraft/entity/Entity/getEyeHeight" "()F"
pin_method "net/minecraft/util/DamageSource/getEntity" "()Lnet/minecraft/entity/Entity;"
pin_field "net/minecraft/util/Vec3/xCoord"
pin_field "net/minecraft/util/Vec3/yCoord"
pin_field "net/minecraft/util/Vec3/zCoord"
echo "ok b3-live : stubs pinned to SRG"

# 2. Provision the 1614 server once (idempotent, checksum-verified).
#    B3_OFFLINE=1 never touches the network: missing cache fails loudly.
mkdir -p "$B3_DIR"
SERV="$B3_DIR/server"
mkdir -p "$SERV"
# 2a. B3_DIR preflight (docker root-owned leftovers fail fast, loudly).
live_preflight_dir "B3_DIR" "$B3_DIR"
live_fetch "$B3_DIR/forge-installer.jar" "$FORGE_URL" "$INSTALLER_SHA1" "${B3_OFFLINE:-0}"
UNI="$SERV/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar"
live_install_server "$SERV" "$B3_DIR/forge-installer.jar" "$J8"
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
#     the universal, which is why forge/ compiles against stubs, not it.
#     The obf name below is 1614-pinned bytes like the universal sha1
#     above: aji takes String and returns aji, the getBlockFromName shape,
#     so aji is Block here.)
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$WorldTickEvent' 'world'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$ClientTickEvent' 'ClientTickEvent('
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$ServerTickEvent' 'ServerTickEvent('
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent' 'side'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent' 'phase'
pin_uni 'cpw.mods.fml.common.gameevent.TickEvent$Phase' 'END'
pin_uni 'cpw.mods.fml.relauncher.Side' 'SERVER'
pin_uni 'cpw.mods.fml.common.Mod' 'modid()'
pin_uni 'cpw.mods.fml.common.Mod' 'dependencies()'
pin_uni 'cpw.mods.fml.common.FMLCommonHandler' 'instance()'
pin_uni 'cpw.mods.fml.common.FMLCommonHandler' 'getSide()'
pin_uni 'cpw.mods.fml.common.registry.EntityRegistry' 'registerModEntity('
pin_uni 'cpw.mods.fml.common.registry.EntityRegistry' 'lookupModSpawn('
pin_uni 'cpw.mods.fml.client.registry.RenderingRegistry' 'registerEntityRenderingHandler('
pin_uni 'cpw.mods.fml.common.eventhandler.EventBus' 'register('
pin_uni 'net.minecraftforge.common.MinecraftForge' 'EVENT_BUS'
pin_uni 'net.minecraftforge.event.world.BlockEvent' 'public final int x;'
pin_uni 'net.minecraftforge.event.world.BlockEvent' 'public final int y;'
pin_uni 'net.minecraftforge.event.world.BlockEvent' 'public final int z;'
pin_uni 'net.minecraftforge.event.world.BlockEvent' ' world;'
pin_uni 'net.minecraftforge.event.world.BlockEvent' ' block;'
pin_uni 'net.minecraftforge.event.world.BlockEvent$BreakEvent' 'BreakEvent('
pin_uni 'net.minecraftforge.event.world.BlockEvent$HarvestDropsEvent' 'HarvestDropsEvent('
pin_uni 'net.minecraftforge.event.entity.living.LivingEvent' 'entityLiving'
pin_uni 'net.minecraftforge.event.entity.living.LivingDropsEvent' 'LivingDropsEvent('
pin_uni 'net.minecraftforge.event.entity.living.LivingHurtEvent' 'LivingHurtEvent('
pin_uni 'net.minecraftforge.event.entity.living.LivingHurtEvent' 'source;'
pin_uni 'net.minecraftforge.event.entity.living.LivingHurtEvent' 'ammount;'
pin_uni 'cpw.mods.fml.common.registry.GameRegistry' 'registerBlock(aji, java.lang.String)'
pin_uni 'cpw.mods.fml.common.registry.GameRegistry' 'registerItem(adb, java.lang.String)'
pin_uni 'cpw.mods.fml.common.registry.GameRegistry' 'findItem(java.lang.String, java.lang.String)'
pin_uni 'cpw.mods.fml.common.event.FMLPreInitializationEvent' 'FMLPreInitializationEvent('
# Renderer tranche: the client frame event the instanced overlay
# subscribes to (Forge-added, never obfuscated — presence is the pin,
# same as every row above; 1614 carries partialTicks as a public field,
# measured via javap on this same universal).
pin_uni 'net.minecraftforge.client.event.RenderWorldLastEvent' 'partialTicks'
echo "ok b3-live : forge stubs pinned to universal"

# 3. Build all mod jars with Java 8. forge/ compiles against the pinned
#    stubs (vanilla shape + Forge shape); the live run is the semantic arbiter.
#    R2: jar entries are sorted with timestamps clamped to EPOCH (same
#    commit + same toolchain == same bytes, see normjar), manifests carry
#    VERSION, the bridge jar embeds mcmod.info.
#    These are the exact bytes the live run proves AND the release ships.
#    The repop spike stage (java/src: bridge-owned MinedStore + pure
#    RepopJob + RepopSeal) compiles into the forge classes dir so the hook
#    links — java/test never ships (etage-1 gate only).
BLD="$B3_DIR/build"
rm -rf "$BLD" \
  || { echo "FAIL b3-live : cannot clear <$BLD> (root-owned docker leftovers? point B3_DIR at a user-owned dir)"; exit 1; }
mkdir -p "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/jars"
"$J8/javac" -source 8 -target 8 -nowarn -d "$BLD/spi" $(find ../spi/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi" -d "$BLD/ex1" $(find ../example1/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi" -d "$BLD/mini" $(find ../minimap/java/src -name '*.java')
"$J8/javac" -source 8 -target 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD/forge" $(find java/src tools/live/stub forge/src -name '*.java')
# 3b. @SideOnly must survive into RuntimeVisibleAnnotations on the exact
#     compiled bytes: Forge 1.7.10 strips client-only methods on dedicated
#     servers only when the annotation is runtime-visible (real SideOnly is
#     RUNTIME, measured by javap on the pinned universal). A stub without
#     retention compiles it invisible, the strip silently misses, and mod
#     load dies resolving client classes (NoClassDefFoundError: ModelPig —
#     found live on T4 bytes, never again silently). javap -v names the
#     block, never the pool ref.
"$J8/javap" -v -p -cp "$BLD/forge" fr.iamacat.bridge.forge.Example1Mod > "$BLD/sideonly-javap.txt"
python3 - "$BLD/sideonly-javap.txt" <<'EOF'
import sys
txt = open(sys.argv[1]).read()
i = txt.find("registerBeastRenderer();")
if i < 0:
    print("FAIL b3-live : registerBeastRenderer absent from javap")
    sys.exit(1)
block = txt[i:i + 4000]
j = block.find("RuntimeVisibleAnnotations")
k = block.find("RuntimeInvisibleAnnotations")
if j < 0 or (k >= 0 and k < j):
    print("FAIL b3-live : @SideOnly invisible on registerBeastRenderer (server strip would miss it)")
    sys.exit(1)
print("ok b3-live : SideOnly runtime-visible on registerBeastRenderer")
EOF
EPOCH="${SOURCE_DATE_EPOCH:-$(git log -1 --format=%ct)}"
printf 'Manifest-Version: 1.0\nImplementation-Version: %s\n' "$VERSION" > "$BLD/MANIFEST.MF"
cat > "$BLD/mcmod.info" <<EOF
[{"modid": "matoubridge", "name": "MatouBridge", "description": "SPI bridge for Minecraft 1.7.10 (reobfuscated SRG).", "version": "$VERSION", "mcversion": "1.7.10", "authorList": ["matou-dev"], "url": "https://github.com/matou-dev/bridge-1710"}, {"modid": "example1", "name": "MatouExample1", "description": "Example1 content: registers example1 blocks (preInit) for the bridge wire.", "version": "$VERSION", "mcversion": "1.7.10", "authorList": ["matou-dev"], "url": "https://github.com/matou-dev/example1"}]
EOF
find "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/MANIFEST.MF" "$BLD/mcmod.info" -exec touch -h -d "@$EPOCH" {} +
mkjar "$BLD/jars/matou-spi.jar" "$BLD/spi"
mkjar "$BLD/jars/matou-example1.jar" "$BLD/ex1"
mkjar "$BLD/jars/matou-minimap.jar" "$BLD/mini"
rm -rf "$BLD/bridgemod" && mkdir -p "$BLD/bridgemod"
cp -r "$BLD/forge/"* "$BLD/bridgemod/"
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
  cp ../example1/content/owned.matou ../example1/content/additive.matou ../example1/content/structure.matou ../example1/content/vein.matou dist/matou-content/
  cp tools/live/my_beast.geo.json dist/my_beast.geo.json
  printf '# Copy to <server>/config/matoubridge/packs.cfg and replace <SERVER>.\n# Wire y=63 keeps plane cells on their own slice, off the structure slices (64..65).\n# The wire block is the registered custom ore (preInit registers example1:my_ore from owned.matou); aliases stay vanilla stone.\n# Vein clusters land on the BASE_Y=60 band (slices 60..61) as the registered ore via the veinblock alias.\nfr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=<SERVER>/matou-content/owned.matou scatterFile=<SERVER>/matou-content/additive.matou structureFile=<SERVER>/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone veinFile=<SERVER>/matou-content/vein.matou veinblock.example1.content:my_ore=example1:my_ore\n' > dist/packs.cfg.example
  (cd dist && sha256sum "matou-spi-$VERSION.jar" "matou-example1-$VERSION.jar" "matou-minimap-$VERSION.jar" "matoubridge-$VERSION.jar" matou-content/owned.matou matou-content/additive.matou matou-content/structure.matou matou-content/vein.matou packs.cfg.example my_beast.geo.json > SHA256SUMS.txt)
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
# Wire y=63: plane cells stay on their own slice, off the structure
# slices (64..65), so the verdict stays per-shape sensitive despite the
# set collapse (a 2D and a 3D cell can share x,z, never y). Vein
# clusters land on their own band (BASE_Y=60, slices 60..61) as the
# registered ore through the veinblock alias.
printf 'fr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=%s/matou-content/owned.matou scatterFile=%s/matou-content/additive.matou structureFile=%s/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone veinFile=%s/matou-content/vein.matou veinblock.example1.content:my_ore=example1:my_ore\n' "$SERV" "$SERV" "$SERV" "$SERV" > "$SERV/config/matoubridge/packs.cfg"
# Beast shape: the shipped Blockbench geometry the renderer bakes and the
# hitboxes derive from (hub decisions/MATOU_MODEL.md, ported from 1122).
# Deployed beside packs.cfg, operator-replaceable like it. The server
# ignores it cleanly (client-only path — zero E_MODEL_* server-side or
# the step 6 grep below fails loudly).
cp tools/live/my_beast.geo.json "$SERV/config/matoubridge/my_beast.geo.json"
echo "eula=true" > "$SERV/eula.txt"
printf 'online-mode=false\nlevel-type=FLAT\ngamemode=1\ndifficulty=0\nmotd=B3 live proof\nmax-tick-time=-1\n' > "$SERV/server.properties"
rm -rf "$SERV/world" "$SERV/logs"
live_boot "$SERV" "$BOOT_SECS" "boot-b3.log" "$J8/java" -Xmx1G -jar "$UNI" nogui

# 6. Fail loudly on any runtime refusal or linkage error. E_MODEL rides
# here since the model tranche (same as 1122: the server ignores the geo
# cleanly, any server-side model refusal fails loudly instead of passing
# silently). E_HIT rides it since the combat tranche (same as 1122: the
# hook refuses corrupt attacker state loudly out of SPI).
live_verdict "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|E_LOOT\|E_SPAWN\|E_MODEL\|E_HIT\|Encountered an unexpected exception" "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|E_LOOT\|E_SPAWN\|E_MODEL\|E_HIT\|Caused by" "$SERV/boot-b3.log"

# 7. Positive proof: world blocks in chunks (0..1, -1..1) at y=60..61
#    plus y=63..65 must equal the pure decision union — nothing foreign,
#    nothing missing.
#    Plane cells land the wire block at y=63 (the registered custom ore,
#    whose runtime numeric ID is dynamic — resolved below from the
#    preInit registration line in the boot log, never hardcoded); volume
#    cells land at their own y=64..65 under their landable names (vanilla
#    stone, frozen ID 1); vein clusters land at their own y=60..61 as the
#    registered ore (dynamic ID, same table). Structure offsets reach
#    x,z=17, and the hut anchor z=-4 spills into chunk row -1 (region
#    r.0.-1.mca) — hence the 6-chunk, 5-slice read.
"$J8/javac" -cp "$BLD/spi:$BLD/ex1" -d "$BLD" tools/live/CellUnion.java
"$J8/java" -cp "$BLD:$BLD/spi:$BLD/ex1" CellUnion \
  "$SERV/config/matoubridge/packs.cfg" 4000 "$BLD/union.txt"
ORE_ID=$(grep -a -o '\[MatouBridge\] registered <example1:my_ore> id [0-9][0-9]*' "$SERV/boot-b3.log" | tail -n 1 | grep -a -o '[0-9][0-9]*$' || true)
[ -n "$ORE_ID" ] || { echo "FAIL b3-live : my_ore registration line absent from boot log (preInit never registered? see $SERV/boot-b3.log)"; exit 1; }
echo "ok b3-live : my_ore id $ORE_ID (dynamic, from boot log)"
live_anvil_loop "$SERV" "$BLD"
live_compare_ids "$BLD/union.txt" "$BLD/world.txt" "$SERV/config/matoubridge/packs.cfg" "minecraft:stone=1,example1:my_ore=$ORE_ID"
