#!/bin/sh
# Gate anti-contamination : le bridge ne réimporte jamais l'ancienne lib.
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
# M1 walking-skeleton gate : compile contre le checkout sibling ../spi
# (convention siblings, cf. hub README). Refus bruyant si absent.
SPI=../spi/java/src
[ -d "$SPI" ] || { echo "FAIL bridge-skeleton : spi sibling absent (cloner hub+spi+bridge-1710 en siblings)"; exit 1; }
mkdir -p java/build
javac --release 8 -d java/build $(find "$SPI" java/src -name '*.java')
javac --release 8 -cp java/build -d java/build $(find java/test -name '*.java')
java -cp java/build fr.iamacat.bridge.BridgeCheck
