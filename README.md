# matou-dev/bridge-1710 — traducteur SPI ↔ Minecraft 1.7.10

Seul module autorisé à importer `net.minecraft` / Forge 1.7.10. Traduit la
`matou-spi` vers le jeu (monde, registres, rendu, RPC dev). Contamination
interdite : aucun import de l'ancienne `fr.iamacat.matoulib` (gate `check`).

Modid : `matoubridge` (cf. `NAMES.md`).

Walking skeleton M1 : `SpiBridge` (`java/src/fr/iamacat/bridge`) pur sans MC,
self-test `java/test`, gate `tools/check.sh` (compile contre le sibling
`../spi`). `TODO(FORGE)` marque le point de branchement FML.
