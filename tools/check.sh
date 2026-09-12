#!/bin/sh
# Gate bridge-1710 : anti-contamination + skeleton pur + forge isole + live.
# Etage 1 (toujours vert, sans MC) : zero-MC sur java/ + compile contre le
# sibling ../spi + BridgeCheck + ModelWireCheck. Etage 2 (Forge) : compile
# forge/ contre tools/live/stub (shape-only, jamais execute) — vert sans
# MC_JAR, plus autoplay-compile tripwire ; MC_JAR recompile contre le vrai
# MC quand fourni, skip sinon. Etage 3 (live, R1) :
# LIVE=1 runs tools/run-live.sh (needs Java 8 + 1614 SRG + network once,
# B3_OFFLINE=1 reuses cache), skip otherwise. Jamais de chemin machine
# en dur ici : l'env pertinent s'exporte (ex. MC_JAR=<minecraft>.jar).
set -eu
cd "$(dirname "$0")/.."
hits=$(rg -n --no-heading "fr\.iamacat\.matoulib" \
  --glob '!tools/**' --glob '!.git/**' --glob '!*.md' . || true)
if [ -n "$hits" ]; then
  echo "FAIL no-legacy-matoulib :"
  echo "$hits"
  exit 1
fi
echo "ok (no-legacy-matoulib)"
# Etage 1 : java/ pur ne touche jamais net.minecraft / cpw.mods.
# Seuls les imports comptent : les commentaires peuvent les nommer.
mc_hits=$(rg -n --no-heading "^\s*import\s+(net\.minecraft|cpw\.mods)" \
  java --glob '!build/**' || true)
if [ -n "$mc_hits" ]; then
  echo "FAIL zero-mc-bridge :"
  echo "$mc_hits"
  exit 1
fi
echo "ok (zero-mc-bridge)"
# Etage 1 : les stubs ne tournent jamais (compile classpath only) — un
# final sur un primitif y est une constante compile-time que javac plie
# dans les bytes prod au lieu de lire le live (mesuré 2026-09-09 :
# event.x/y/z pliés à 0,0,0 dans le hook spike, attrapé live). Refus.
const_hits=$(rg -n --no-heading "final\s+(byte|short|int|long|float|double|boolean|char)\s+\w+\s*=" \
  tools/live/stub tools/autoplay/stub 2>/dev/null || true)
if [ -n "$const_hits" ]; then
  echo "FAIL no-stub-const :"
  echo "$const_hits"
  exit 1
fi
echo "ok (no-stub-const)"
# T3 registry gate, closed by T4 pack-driven (hub
# decisions/SPI_STATE_VOCABULARY.md): neither java/ nor forge/ knows any
# content by import — tables, jobs and kinds ride the SPI PolicyPack
# served by the reflectively loaded pack (AGENTS.md sect. 3). Seuls les
# imports comptent : les commentaires peuvent les nommer. Les tests
# gardent leurs imports (le comparateur lit les deux cotes).
ex1_hits=$(rg -n --no-heading "^\s*import\s+fr\.iamacat\.example1" \
  java/src forge/src || true)
if [ -n "$ex1_hits" ]; then
  echo "FAIL no-lateral-import :"
  echo "$ex1_hits"
  exit 1
fi
echo "ok (no-lateral-import)"
# M1 walking-skeleton gate : compile contre les checkouts siblings ../spi
# et ../example1 (convention siblings, cf. hub README). Refus bruyant.
SPI=../spi/java/src
[ -d "$SPI" ] || { echo "FAIL bridge-skeleton : spi sibling absent (cloner hub+spi+bridge-1710 en siblings)"; exit 1; }
EX1=../example1/java/src
[ -d "$EX1" ] || { echo "FAIL bridge-content : example1 sibling absent (cloner hub+spi+bridge-1710+example1 en siblings)"; exit 1; }
[ -f ../example1/content/owned.matou ] || { echo "FAIL bridge-content : example1 content absent"; exit 1; }
# SPI_PIN : ce bridge est valide contre ce SPI-la, pas un autre. Un sibling
# qui ne matche pas = bridge en avance/retard — re-valider puis bumper.
PIN=$(tr -d '[:space:]' < SPI_PIN)
[ -n "$PIN" ] || { echo "FAIL spi-pin : empty SPI_PIN"; exit 1; }
want=$(git -C ../spi rev-list -n 1 "$PIN" 2>/dev/null) || { echo "FAIL spi-pin : unknown pin <$PIN> (fetch tags?)"; exit 1; }
got=$(git -C ../spi rev-parse HEAD) || { echo "FAIL spi-pin : ../spi not a git checkout"; exit 1; }
[ "$want" = "$got" ] || { echo "FAIL spi-pin : want $PIN ($want), sibling $got (re-validate, then bump SPI_PIN)"; exit 1; }
echo "ok (spi-pin : $PIN)"
mkdir -p java/build
javac --release 8 -d java/build $(find "$SPI" "$EX1" java/src -name '*.java')
javac --release 8 -cp java/build -d java/build $(find java/test -name '*.java')
java -cp java/build fr.iamacat.bridge.ForgeContentCheck
java -cp java/build fr.iamacat.bridge.spike.RepopCheck
java -cp java/build fr.iamacat.bridge.loot.LootCheck
java -cp java/build fr.iamacat.bridge.spawn.SpawnCheck
java -cp java/build fr.iamacat.bridge.model.ModelWireCheck
java -cp java/build fr.iamacat.bridge.render.RenderWireCheck
# Etage 2 : forge/ seul touche MC/Forge (10.13.4.1614). Stub shape-only,
# pas de MC_JAR requis : vert partout, le live B3 prouve contre le vrai
# jar (etage 3, LIVE=1). Porte du pattern 1122 (forge-2860-stub).
# Clean before compile: javac never deletes stale classes, so a renamed or
# deleted source would linger in forge/build and lie to surface scans.
rm -rf forge/build && mkdir -p forge/build
javac --release 8 -cp java/build -d forge/build $(find forge/src tools/live/stub -name '*.java')
echo "ok (forge-1614-stub)"
# Visual-tranche tripwire (found on 1122 2026-09-11, ported here with the
# renderer: the autoplay companion had not compiled since the item
# tranche because no gate built it, only run-client.sh AUTOPLAY=1 did, at
# run time). DEV-only compile against stubs, never shipped, never run
# here; tools/autoplay/stub is optional (1122 merged its shapes into
# tools/live/stub — one Minecraft class only, duplicates never compile).
mkdir -p build/auto
AUTO_SRC="tools/autoplay/src tools/live/stub"
[ -d tools/autoplay/stub ] && AUTO_SRC="$AUTO_SRC tools/autoplay/stub"
# shellcheck disable=SC2086
javac --release 8 -cp java/build:forge/build -d build/auto $(find $AUTO_SRC -name '*.java')
echo "ok (autoplay-compile)"
# Etage 2bis (Forge 10.13.4.1614, legacy) : recompile forge/ seul contre
# le vrai MC 1.7.10 quand MC_JAR est fourni, skip sinon (le stub ci-dessus
# est le gate vert partout). Etage 3 (live, R1) :
if [ -z "${MC_JAR:-}" ]; then
  echo "skip forge (no MC_JAR)"
else
  [ -f "$MC_JAR" ] || { echo "FAIL forge : MC_JAR=<$MC_JAR> missing"; exit 1; }
  # Clean before compile: javac never deletes stale classes, so a renamed or
  # deleted source would linger in forge/build and lie to surface scans.
  rm -rf forge/build && mkdir -p forge/build
  javac --release 8 -cp "java/build:$MC_JAR" -d forge/build $(find forge/src -name '*.java')
  echo "ok (forge-1614)"
fi
# Etage 3 (R1) : live opt-in. Default skip keeps CI green without
# network/Java 8/SRG; LIVE=1 fails loudly without them, never silently.
if [ "${LIVE:-}" != "1" ]; then
  echo "skip live (LIVE!=1)"
  exit 0
fi
exec sh tools/run-live.sh
