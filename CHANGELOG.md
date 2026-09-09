# Changelog — matou-dev/bridge-1710

Notable changes to this repo. The bridge jar is the only loadable Forge
mod and it never ships alone: releases are versioned source + server drops
(`dist/`, reproducible). Store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/bridge-1710/releases.

## [Unreleased]

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
