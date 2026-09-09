# matou-dev/bridge-1710 — SPI ↔ Minecraft 1.7.10 translator

The only module allowed to import `net.minecraft` / Forge 1.7.10. Translates
`matou-spi` into the game (world, registries, rendering, dev RPC).
Contamination forbidden: no import of the legacy `fr.iamacat.matoulib`
(`check` gate).

Modid: `matoubridge` (see `NAMES.md`).

Walking skeleton M1: `SpiBridge` (shared seam from `matou-spi` v1.1.0,
`fr.iamacat.bridge`) pure without MC, self-test `java/test`
(`ForgeContentCheck`), gate `tools/check.sh` (compiles against the
`../spi` sibling). `TODO(FORGE)` marks the FML branching point.
`SPI_PIN` pins the validated SPI (hub `check-bridges.sh` refuses bridge
drift: pins, forge file-set, `E_FORGE_*` catalog).

## B1 Forge wiring (Forge 10.13.4.1614)

Two-stage isolation, only `forge/` touches MC:

- Shared apply seam from `matou-spi` v1.1.0 (`fr.iamacat.bridge`, pure,
  zero MC): `SpiBridge` (decide→apply), `CellSink` (application seam:
  `setCell` for `x,z` plane cells, `setBlock` for `x,y,z:ns:block`
  volume cells, 2D-only sinks refuse 3D loudly), `ForgeCells` (parse
  both shapes + apply verbatim with dispatch, loud refusals),
  `ForgeSnapshot` (seals maps into an immutable `Snapshot`). This repo
  carries only its Forge side below; the seam itself is tested jar-free
  by `BridgeCheck` in `matou-spi`.
- `forge/src/fr/iamacat/bridge/forge` (MC only): `MatouBridgeMod`
  (`@Mod(modid="matoubridge")`, FML server tick `END` → snapshot
  `matou:tick` → `SpiBridge.tick`), `WorldCellSink` (`CellSink` into
  `World.setBlock`, y `0..255`; volume cells resolve their block by name,
  cached, unknown refused loudly). Passive B1 job (decides nothing, writes
  nothing, Q1 coexistence); content wiring (example1) follows in B2 on this
  seam.

Gate: `./tools/check.sh` (stage 1 pure, always green without MC),
`MC_JAR=<minecraft-1.7.10>.jar ./tools/check.sh` for the Forge stage 2
(compiles `forge/` against MC 1.7.10, skips without `MC_JAR`). No hardcoded
machine path in this repo.

## B2 content wiring (packs)

The bridge stays content-blind at build time (Q2): packs are discovered by
class name from `config/matoubridge/packs.cfg`
(`<class> <y> <block> [k=v ...]`, `#` comments, missing file =
passive like B1). Contract in `matou-spi` (`ContentPack` + optional
`ConfigurablePack`, public no-arg constructor); `ExamplePack` on the
example1 side (counts re-read from the `.matou` sources via the SPI parser,
never hardcoded).

- Pure, MC-free tested: `ForgeContent` (seal → decide → owned-first merge →
  apply), `Packs` (strict config parse + reflective `load`), E2E
  `ForgeContentCheck` (real example1 jobs from the source files + fake
  world; `ForgeContent.merge == AdditiveScatterJob.merge` comparator;
  wired-pack decide keeps the legacy union first + 18 volume cells, and a
  2D-only sink refuses the wired pack loudly).
- FML (`forge/`, MC only): `PackWire.bind` (load + configure + block +
  y `0..255`, fail fast at init), `MatouBridgeMod.onWorldTick`
  (server, `END`, dimension 0 → `applyTo`).
- Stage 2 = compile proof against Forge 1614 (off-game MC runtime lacks
  classpath: Guava...; live behaviour is proven in-game, not here).

## B3 live proof (Forge 1614 server run)

Stage 2 compiles; only the game arbitrates runtime. B3 runs a real
1614 dedicated server with the built jars and compares the world against
the pure decision union — see `tools/run-live.sh` (manual gate, needs
network once + Java 8; env-driven, no machine paths: `B3_DIR` /
`JAVA8_HOME` / `BOOT_SECS` optional, `SRG_MCP` auto-discovers the
ForgeGradle 1614 cache under `$HOME` unless overridden).

- `forge/` compiles against pinned stubs (`tools/live/stub/`): every
  stubbed vanilla member is asserted in the exact SRG used for reobf,
  every stubbed Forge member in the provisioned universal jar. Stub
  drift fails loudly; stubs never ship (leak check before jarring).
- The bridge jar is reobfuscated MCP→SRG (`tools/live/Reobf.java`, the
  ForgeGradle `reobf` equivalent): runtime vanilla only declares SRG
  names, so an un-reobfed jar dies linking.
- Verdict: boot with zero `NoSuch*`/`E_*` refusals, then chunks (0..1, -1..1)
  at y=63..65 must equal the pure `ForgeContent.decideAll` union over the
  live `packs.cfg` — plane cells at the wire y=63, volume cells at their
  own y=64..65 (separate slices keep the verdict per-shape sensitive) —
  stone only, nothing foreign, nothing missing (`tools/live/anvil.py`
  + `CellUnion`).

Live already paid for itself: the first B3 run caught two linkage bugs
stage 2 could never see — `Block.getBlockFromName` and `World.provider`
left un-remapped (`NoSuchMethodError`/`NoSuchFieldError` at init/tick),
fixed by the reobf step, not by source changes (the sources were valid
1.7.10 MCP all along).

## R1 live reproducibility

- Pins: installer / universal / `srg-mcp.srg` sha1 verified on every run,
  pinned ASM (`asm-all-5.0.3.jar`); any upstream drift fails loudly.
- Cache: server provisioned once under `B3_DIR` (idempotent);
  `B3_OFFLINE=1` reuses the cache and never downloads.
- Docker: `tools/live/Dockerfile` (JDK 8 + python3 + curl + git) reproduces the
  runner without installing Java 8 on the host. The image runs as a
  non-root `builder` user — build with
  `--build-arg UID=$(id -u) --build-arg GID=$(id -g)` so bind-mounted
  `B3_DIR` checkouts keep host ownership (pre-R3 root-owned `build/`
  leftovers must be cleared once).
- Opt-in gate: `LIVE=1 ./tools/check.sh` runs the live proof after etages
  1-2; default stays green without network / Java 8 / SRG (same skip
  pattern as `MC_JAR`).

## R2 release engineering

`BUILD_ONLY=1 VERSION=x.y.z ./tools/run-live.sh` assembles `dist/` (gitignored)
and exits before booting the server:

- `matou-spi-<V>.jar`, `matou-example1-<V>.jar`, `matou-minimap-<V>.jar`
  (pure, zero-MC), `matoubridge-<V>.jar` (reobfuscated SRG, embeds
  `mcmod.info`), `matou-content/*.matou`, `packs.cfg.example`, `SHA256SUMS.txt`.
- Same jar-creation path as the live run (sorted entries, mtimes pinned to
  `SOURCE_DATE_EPOCH` or bridge HEAD time, manifest `Implementation-Version`),
  so the live proof (`world == pure union`) covers the exact shipped bytes —
  the MCP-named bridge jar never ships, only the reobf one.
- Release guards fail loudly: `VERSION` must be strict `X.Y.Z`, the 4 code
  repos must have clean trees, and `@Mod version` must equal `VERSION`
  (bump `MatouBridgeMod` source first, never the tag alone).
- Store listings (Modrinth/CurseForge, see hub `NAMES.md`) stay DRAFT: only
  the bridge jar is a loadable Forge mod and it never ships alone, so v1.0.0
  is a versioned source + server-drop release (git tags + GitHub releases),
  not a store publish.

## R3 hygiene

- Docs in English, per-repo `CHANGELOG.md`, public-ready READMEs (repos
  stay private during dev).
- Docker root-cause fix: non-root image user (see R1); the loud
  `cannot clear <build>` guard in `run-live.sh` stays as defence in depth.

## Java 8 / modern-Java compatibility

Shipped jars stay Java 8 bytecode (major 52, no `module-info`, no
multi-release) — enforced by `tools/run-live.sh` on every live and release
run, so the same bytes load on Java 8 and on modern JVMs:

| Runtime | Setup | Proven |
|---|---|---|
| Java 8, vanilla Forge 1614 | `sh tools/run-live.sh` | yes (hub `STATE.md`) |
| Java 17/21 + lwjgl3ify | upstream `forgePatches` + `java9args.txt` | no (procedure only) |

Rules: never raise bytecode above 52, never use post-8 JDK APIs
(`--release 8` gates), never compile against LWJGL3 — future client
rendering targets the LWJGL2 API and lets lwjgl3ify redirect it.
