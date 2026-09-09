#!/bin/sh
# Pointer (SSOT = hub/tools/verify-client-save.sh): thin exec shim.
set -eu
HUB="$(dirname "$0")/../../hub"
[ -f "$HUB/tools/verify-client-save.sh" ] \
  || { echo "FAIL verify-client : hub sibling absent ($HUB/tools/verify-client-save.sh, clone hub+bridges as siblings)"; exit 1; }
BRIDGE="$(dirname "$0")/.."
export BRIDGE
exec sh "$HUB/tools/verify-client-save.sh" "$@"
