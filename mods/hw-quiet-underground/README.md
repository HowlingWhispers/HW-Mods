# HW Quiet Underground 1.0.0

A **development-only** world-generation data pack prototype for **Minecraft Java 26.4 Snapshot 3**, built from that snapshot's exact vanilla definitions. It works with CodaLoader because vanilla Minecraft loads world data packs, but it is **not** a CML mod JAR or an automatically installed loader module yet.

## Effects

- **Giant cheese caverns:** The vanilla cheese-cave density term is allowed only inside rare noise-selected underground regions. Elsewhere the term is replaced by positive density, suppressing giant chambers while retaining other cave systems. Noise selection is an approximate, spatial rarity control, **not** a verified 80% reduction in cavern count.
- **Ravines / canyons:** Spawn probability per eligible carver seed-chunk reduced from `0.01` to `0.002`, exactly one-fifth vanilla's probability (five times rarer).
- **Other caves:** Keeps standard cave carver probability `0.15` and extra-underground cave carver probability `0.07`. Disables the vanilla 10% chance of unusually wide cave tunnel bias in both, so ordinary caves still appear.
- **Untouched:** Ore rules, biomes (including ice caves), structures, underwater aquifers, the Nether, the End, and existing chunks.

The rare cheese-cave regions are selected using a low-frequency sample of `minecraft:cave_cheese` noise. Region boundaries may have abrupt underground transitions. Snapshot gameplay validation is still needed; this is an experimental terrain preset.

## Developer testing only

H.O.W.L. does not offer optional first-party player mods. This experimental pack is **not a playable-release download**. Its manual steps below are for disposable test worlds only. When worldgen is verified, integration must happen automatically for newly created H.O.W.L. worlds without a player opt-in. Existing saves must not be silently changed.

## How to test it

1. Build `dist/hw-quiet-underground-1.0.0.zip` from the repository with `python3 scripts/build-hw-quiet-underground.py`, for local development tests.
2. **Disposable test world:** From the Create New World screen, open **Data Packs / Open Pack Folder**, copy the ZIP there, enable **HW Quiet Underground**, then create the world.
3. **Do not apply to existing player worlds.** Experimental terrain changes can produce chunk-border discontinuities; tests belong in isolated saves.
4. Do **not** extract the ZIP inside the datapacks folder. `pack.mcmeta` is at ZIP root.

Minecraft Snapshot 3 data-pack format: **123.0**. This pack must be activated as a **world data pack**, not dropped into the CodaLoader `mods` directory.

This first build intentionally does not add automatic world-save mutation to CodaLoader. The release target is built-in, required worldgen for new H.O.W.L. worlds after live validation, not an optional one-click install. Existing-world migration needs explicit backup and compatibility testing.

## Source fidelity

The JSON is based on vanilla files extracted for **26.4-snapshot-3** from `misode/mcmeta` data branch (version JSON identifies snapshot data version 5122). Each override contains only the intentional changes described above. This avoids copying old-worldgen formats into the 26.4 snapshot.

## Validation

```sh
python3 tests/test-hw-quiet-underground.py
python3 scripts/build-hw-quiet-underground.py
```

Tests check metadata, JSON registry shape, exact canyon probability, preserved regular cave probabilities, and the singular gated cheese-cave term. They do **not** replace live world-generation tests.
