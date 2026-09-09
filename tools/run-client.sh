#!/bin/sh
# Pointer (SSOT = hub/tools/run-client.sh): thin exec shim, no logic here.
# Per-version parameters live in hub/tools/client-common.sh.
set -eu
HUB="$(dirname "$0")/../../hub"
[ -f "$HUB/tools/run-client.sh" ] \
  || { echo "FAIL run-client : hub sibling absent ($HUB/tools/run-client.sh, clone hub+bridges as siblings)"; exit 1; }
BRIDGE="$(dirname "$0")/.."
export BRIDGE
exec sh "$HUB/tools/run-client.sh" "$@"
