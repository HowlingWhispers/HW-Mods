# BuildCraft Reborn for H.O.W.L.

**New project, new identity:** `hw_buildcraft_reborn` · **port version:** `0.0.1-dev.1`.

This is an independent **source-first port** of the already functioning
[BuildCraft Community Edition](https://github.com/BCCE-team/BuildCraft)
**8.0.23**, pinned to the exact commit in `UPSTREAM.lock.json`. Its
original BuildCraft Java packages, source structure, textures and mechanics
are the authority. We are **not recreating** their pipes or engines.

## Deliberate separation

- Project root: `projects/buildcraft-reborn/` (NOT `mods/buildcraft-cml/`).
- Reserved future mod ID: `hw_buildcraft_reborn` (NOT `buildcraft_cml`).
- Reserved JAR name: `hw-buildcraft-reborn-<version>.jar` (NOT
  `buildcraft-cml-0.1.0-dev.jar`).
- Isolated full-original-source materialization: `dist/buildcraft-reborn/`.
- Dedicated CI workflow: `.github/workflows/buildcraft-reborn.yml`.
- Neither CodaLauncher nor H.O.W.L.'s mod catalog installs this project yet.
- `vendor/bcce-8.0.23/` contains **byte-for-byte original BCCE source**
  for the first real pipe, engine, renderer and registry systems. The
  `scripts/prepare-source.sh` script materializes the **entire** original
  upstream source from its OWN layered-source tool for further porting.
- The deleted `mods/buildcraft-cml/` experiment is never referenced as a
  source dependency. There are no copied `PipeNetwork` or placeholder
  `BuildCraftRedstoneEngine` implementations here.

## Status

**Source import only. NOT a playable Minecraft mod, NOT a compilation of the
original BuildCraft for Snapshot 3, and NOT a distributable H.O.W.L. JAR.**

The original Minecraft 1.21.11 NeoForge source is the reference. Next we
adapt the original `BlockPipeHolder`, `TilePipeHolder`, `PipeFlowItems`,
`TravellingItem`, original engine classes, and both renderers against
Minecraft 26.4 Snapshot 3 and H.O.W.L. Nothing is called "playable" until
original item motion and piston animation work with save/reload checks.

## Rebuild the exact original reference (Linux/macOS/CI)

```bash
bash projects/buildcraft-reborn/scripts/prepare-source.sh
```

This fetches the **exact** upstream commit, verifies its official 8.0.23
version and license, materializes the 1.21.11-neoforge target with BCCE's
own `scripts/source_layout.py`, and compares all vendored files against
that pinned original byte-for-byte. It never calls any old BuildCraft
project script.

Original sources retain the upstream MPL-2.0 license. See
`vendor/bcce-8.0.23/LICENSE.txt` and `UPSTREAM.lock.json`.
