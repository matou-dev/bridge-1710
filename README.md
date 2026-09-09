# matou-dev/bridge-1710 — traducteur SPI ↔ Minecraft 1.7.10

Seul module autorisé à importer `net.minecraft` / Forge 1.7.10. Traduit la
`matou-spi` vers le jeu (monde, registres, rendu, RPC dev). Contamination
interdite : aucun import de l'ancienne `fr.iamacat.matoulib` (gate `check`).

Modid : `matoubridge` (cf. `NAMES.md`).

Walking skeleton M1 : `SpiBridge` (`java/src/fr/iamacat/bridge`) pur sans MC,
self-test `java/test`, gate `tools/check.sh` (compile contre le sibling
`../spi`). `TODO(FORGE)` marque le point de branchement FML.

## B1 Forge wiring (Forge 10.13.4.1614)

Isolation 2 étages, seule `forge/` touche MC :

- `java/src/fr/iamacat/bridge` (pur, zéro MC) : `SpiBridge` (decide→apply),
  `CellSink` (seam d'application), `ForgeCells` (parse `x,z` + apply
  verbatim, refus bruyants), `ForgeSnapshot` (scelle les maps en `Snapshot`
  immutable). Testé sans jars par `BridgeCheck`.
- `forge/src/fr/iamacat/bridge/forge` (seul MC) : `MatouBridgeMod`
  (`@Mod(modid="matoubridge")`, tick serveur FML `END` → snapshot
  `matou:tick` → `SpiBridge.tick`), `WorldCellSink` (`CellSink` vers
  `World.setBlock`, y `0..255`). Job B1 passif (décide rien, n'écrit rien,
  cohabitation Q1) ; le câblage contenu (example1) suit en B2 sur ce seam.

Gate : `./tools/check.sh` (étage 1 pur, toujours vert sans MC),
`MC_JAR=<minecraft-1.7.10>.jar ./tools/check.sh` pour l'étage 2 Forge
(compile `forge/` contre MC 1.7.10, skip sans `MC_JAR`). Aucun chemin
machine en dur dans ce repo.

## B2 content wiring (packs)

Le bridge reste aveugle au contenu au build (Q2) : les packs sont
découverts par nom de classe depuis `config/matoubridge/packs.cfg`
(`<class> <y> <block> [k=v ...]`, `#` commentaires, fichier absent =
passif comme B1). Contrat en `matou-spi` (`ContentPack` + optionnel
`ConfigurablePack`, constructeur public sans args) ; `ExamplePack`
côté example1 (counts relus des `.matou` sources via le parser SPI,
jamais en dur).

- Pur testé sans MC : `ForgeContent` (seal → decide → merge owned-first →
  apply), `Packs` (parse config strict + `load` réflexif), E2E
  `ForgeContentCheck` (vrais jobs example1 depuis les fichiers sources +
  faux monde ; comparateur `ForgeContent.merge == AdditiveScatterJob.merge`).
- FML (`forge/`, seul MC) : `PackWire.bind` (load + configure + bloc +
  y `0..255`, échec rapide à l'init), `MatouBridgeMod.onWorldTick`
  (serveur, `END`, dimension 0 → `applyTo`).
- Étage 2 = preuve de compilation contre Forge 1614 (le runtime MC hors
  jeu manque de classpath : Guava... ; le comportement live se prouve en
  jeu, pas ici).
