# BuildCraft Community Edition for H.O.W.L.

**Status: upstream 8.0.23 pinned and reference source materialization implemented. This is not yet a playable one-to-one BuildCraft port.**

- Original source: https://github.com/BCCE-team/BuildCraft
- Official release: `8.0.23` (tag commit `23c6af379676ce5262c5c0cb6f1f331edc9b12c6`)
- Source reference: BCCE's modern **1.21.11 NeoForge** target; BCCE's 1.19.2 implementation remains the upstream behavioral benchmark.
- Port target: **Minecraft Java 26.4 Snapshot 3**, using H.O.W.L. without Forge, NeoForge or Fabric.
- License: MPL-2.0 (upstream notices, original authors and modified-file source obligations must remain intact).

## Current native engine milestone

**Implemented and tested:** the original BCCE API2 `MjAmount` Java source, a loader-neutral port of `TileEngineRedstone_BC8` + `TileEngineBase_BC8` MJ generation/heat/piston timing, redstone-receiver midpoint pulse semantics, strict MJ acceptance checks, and lossless world-local MJ/heat/stroke snapshot restoration. The engine runtime now ticks every server tick against the placed native engine block; it no longer teleports vanilla chest contents in place of real traveling items. The old `/buildcraft pulse` command is no longer registered in-game.

**Not yet implemented:** real Snapshot 3 block-entity ownership, BCCE MJ ports on pipe blocks, mobile item packet entities and rendering, wrench interactions, proper engine orientation, recipes, full pipe set, and all remaining machines/modules. This commit is not a candidate for a player download or normal Nightly release.

Unit and integration checks: `bash scripts/test-buildcraft-integration.sh` with `HW_CODALOADER_API_DIR` pointing at the sibling H.O.W.L. source.

## First engineering milestone

Run from the HW-Mods repository root:

```bash
bash scripts/prepare-buildcraft-bcce.sh
python3 tests/test-buildcraft-bcce-reference.py
```

The first command downloads and verifies the **exact upstream release commit** and uses BCCE's own `source_layout.py` to materialize its complete eight-module modern reference into `dist/buildcraft-reference/1.21.11-neoforge`. It records source provenance and module counts in `HOWL-SOURCE-MANIFEST.json`, and preserves upstream licensing.

**This directory is an untouched NeoForge-target reference, not a H.O.W.L. mod JAR.** Do not bundle it as an executable mod or confuse asset imports with functional game code. BuildCraft APIs, energy, pipe behaviors, UIs, recipes and world state must be ported against H.O.W.L.'s version-pinned Minecraft boundary.

The earlier `buildcraft-cml` Java prototype and experimental Nightly packaging are still present in this repository for reference. They are **not** the upstream BCCE game engine and are not evidence of parity. New work should reuse exact upstream semantics, with documented necessary Minecraft/H.O.W.L. adaptations, not expand an invented virtual pipe system.

See [PORTING-SOURCES.md](PORTING-SOURCES.md) for source ownership, acceptance gates and porting order.

## Release acceptance

The first genuine playtest must have actual original BuildCraft blocks/items registered and a native engine-powered wooden pipe visibly transport items between real chests, surviving save/reload without loss or duplication. Creative inventory crashes, renderer/atlas errors and placeholder glass-pipe commands are release blockers. Follow with the remaining original modules before calling the port complete.
