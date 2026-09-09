# Changelog — matou-dev/bridge-1710

Notable changes to this repo. The bridge jar is the only loadable Forge
mod and it never ships alone: releases are versioned source + server drops
(`dist/`, reproducible). Store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/bridge-1710/releases.

## [Unreleased]

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
