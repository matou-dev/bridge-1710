# Changelog — matou-dev/bridge-1710

Notable changes to this repo. The bridge jar is the only loadable Forge
mod and it never ships alone: releases are versioned source + server drops
(`dist/`, reproducible). Store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/bridge-1710/releases.

## [Unreleased]

- Custom entity tranche (hub `decisions/SPAWN.md`): `MatouEntity`
  (generic beast, pig shape/AI/sounds reused, vanilla health) +
  `Example1Mod` preInit `EntityRegistry.registerModEntity` (short mob
  name, mod-local id 0, pig-like tracking) with init-time
  `lookupModSpawn` tripwire + client-only vanilla `RenderPig` mapping;
  census/veto/reconcile/kill/landing match the beast (vanilla pigs
  ignored); companion counts/kills `MatouEntity` and carries
  `required-after:matoubridge` (ModClassLoader negative-cache CNFE,
  measured live); `E_REG_*` local, no new `E_FORGE_*`.

## [1.2.0] - 2026-09-09

Versioned server drop: https://github.com/matou-dev/bridge-1710/releases/tag/v1.2.0

- Unified v1.2.0 round over `matou-spi` `3e819a9` (shared authoring
  surface: `Cell` + `Counts` + typed `Snapshot` + `Packs.loadConfigured`,
  additive only, merge comparateur still green — no live re-proof,
  decided bytes identical, gates lock them).
- Dev-client helpers as thin wrappers over hub `tools/run-client.sh`
  (experimental, 1165-proven only).

## [1.1.0] - 2026-09-09

Versioned server drop: https://github.com/matou-dev/bridge-1710/releases/tag/v1.1.0

- Shared apply seam moved up to `matou-spi` v1.1.0 at identical FQNs:
  `java/src/fr.iamacat/bridge` (6 files) and `BridgeCheck` deleted here,
  consumed from the `../spi` sibling (already on the gate classpath).
  `ForgeContentCheck` stays: it compares against `../example1`. FQNs and
  behavior unchanged.
- `B3_DIR` preflight guard in `run-live.sh`: any non-owned leftovers under
  `$B3_DIR` (docker root-owned `build/`, `world/`, `logs/`,
  `matou-content/`) fail fast with the fix (`sudo rm -rf` the four dirs or
  a fresh `B3_DIR`), instead of dying mid-run or reusing stale state.
  The clean-dir workaround (`B3_DIR=/tmp/matou-b3-clean`) is now the
  loud default path, not tribal knowledge.
- Live wires the composite `hut` (structureFile + `block.*` stone aliases
  in `packs.cfg`): `CellUnion` now replays the live `packs.cfg` wire
  (reflective load, same configure path as `PackWire.bind`) and the
  verdict reads chunks (0..1, 0..1) at y=64..65 (structure offsets reach
  x,z=17). Plane cells map at the wire y, volume cells at their own y;
  stone-only still holds.

- `run-live.sh` drops the `java/src` build stage (pure seam ships from
  `matou-spi` v1.1.0 since the seam move; only the Forge side builds here),
  mirroring `bridge-1122`.
- 3D landing for V3 structure cells:`CellSink.setBlock` (default refuses
  loudly so 2D-only sinks never swallow volumes), `ForgeCells`
  shape-dispatch in `applyCells` (`:` = volume, else plane, both loud on
  bad shape), `WorldCellSink` override (own y range-checked, block
  resolved by name and cached, unknown refused as `E_FORGE_BLOCK`).
  Forge code uses only already-stubbed members (stubs untouched) and
  compiles against `tools/live/stub`. Release dist now ships
  `structure.matou` (+ SHA) with a commented `structureFile` example;
  live `packs.cfg` stays 2-file until a palette maps to vanilla blocks
  (example1's `hut_wall` would refuse loudly live — by design, follow-up).

- Dual-runtime contract: shipped jars must stay Java 8 bytecode (major 52,
  no `module-info`, no multi-release) — enforced by `run-live.sh` on live
  and release runs; README documents the Java 8 vanilla / 17-21 lwjgl3ify
  matrix.
- Live re-proof 2026-09-09 (post repro-pinning: hub `b730266` / spi
  `66cdb52` / bridge `0bcd2f9` / example1 `ca7e5e2` / minimap `df1383e`,
  host OpenJDK 1.8.0_502): 150s 1614 server run, `PackWire.bind` clean,
  world == pure union (256 cells, stone only). Replay: `sh
  tools/run-live.sh` (manual `live-proof` workflow, needs the 1614 SRG
  cache); last green proof recorded in hub `STATE.md`.
- Repro: live-runner base image pinned by digest
  (`eclipse-temurin:8-jdk-jammy@sha256:0d568cc4…cd9ed0c`); provisioned ASM
  5.0.3 pinned by sha1 (any drift fails loudly, never silently).
- CI: runner pinned (`ubuntu-24.04`), JDK 21 via `setup-java` (temurin),
  actions pinned by SHA with Dependabot, missing `example1` sibling
  checkout added (the gate compiles against it).
- Docker: the live-runner image runs as a non-root `builder` user
  (`--build-arg UID/GID`), so bind-mounted `B3_DIR` checkouts keep host
  ownership (R3; pre-R3 root-owned `build/` leftovers must be cleared once).
- Docs: README fully rewritten in English (R3 hygiene).

## [1.0.0] - 2026-09-09

Versioned server drop: https://github.com/matou-dev/bridge-1710/releases/tag/v1.0.0

- Live-proven full chain (Forge 1.7.10-1614, modid `matoubridge`): pins
  verified, 150s dedicated-server run, `PackWire.bind` clean,
  world == pure decision union (256 cells, stone only).
- `dist/` assembles the exact live-proven bytes (`BUILD_ONLY=1
  VERSION=1.0.0`): `matou-spi`, `matou-example1`, `matou-minimap` (pure,
  zero-MC), `matoubridge` (reobfuscated SRG, embeds `mcmod.info`;
  the MCP-named jar never ships), `matou-content/*.matou`,
  `packs.cfg.example`, `SHA256SUMS.txt`.
- Deterministic build: same commit + same toolchain == same bytes
  (jar timestamps clamped via `normjar`); release guards fail loudly
  (strict `X.Y.Z`, clean trees on all 4 code repos, `@Mod` version ==
  `VERSION`).
