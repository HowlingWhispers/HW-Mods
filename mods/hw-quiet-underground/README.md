# HW Quiet Underground 1.0.0

A world-generation data pack for **Minecraft Java 26.4 Snapshot 3**, built from that snapshot's exact vanilla definitions. It works with CodaLoader because vanilla Minecraft loads world data packs, but it is **not** a CML mod JAR or an automatically installed loader module yet.

## Effects

- **Giant cheese caverns:** The vanilla cheese-cave density term is allowed only inside rare noise-selected underground regions. Elsewhere the term is replaced by positive density, suppressing giant chambers while retaining other cave systems. Noise selection is an approximate, spatial rarity control, **not** a verified 80% reduction in cavern count.
- **Ravines / canyons:** Spawn probability per eligible carver seed-chunk reduced from `0.01` to `0.002`, exactly one-fifth vanilla's probability (five times rarer).
- **Other caves:** Keeps standard cave carver probability `0.15` and extra-underground cave carver probability `0.07`. Disables the vanilla 10% chance of unusually wide cave tunnel bias in both, so ordinary caves still appear.
- **Untouched:** Ore rules, biomes (including ice caves), structures, underwater aquifers, the Nether, the End, and existing chunks.

The rare cheese-cave regions are selected using a low-frequency sample of `minecraft:cave_cheese` noise. Region boundaries may have abrupt underground transitions. Snapshot gameplay validation is still needed; this is an experimental terrain preset.

## How to use it

1. Build `dist/hw-quiet-underground-1.0.0.zip` from the repository with `python3 scripts/build-hw-quiet-underground.py`, or download the built ZIP when published.
2. **New world (recommended):** From the Create New World screen, open **Data Packs / Open Pack Folder**, copy the ZIP there, enable **HW Quiet Underground**, then create the world.
3. **Existing world:** Back up the save, close it, and copy the ZIP to `<game-directory>/saves/<world>/datapacks/`. Existing chunks stay unchanged; only new chunks get the rules. Terrain boundaries may be visible.
4. Do **not** extract the ZIP inside the datapacks folder. `pack.mcmeta` is at ZIP root.

Minecraft Snapshot 3 data-pack format: **123.0**. This pack must be activated as a **world data pack**, not dropped into the CodaLoader `mods` directory.

This first build intentionally does not add automatic world-save mutation to CodaLoader. Native one-click CML installation can be added after the pack has been tested.

## Source fidelity

The JSON is based on vanilla files extracted for **26.4-snapshot-3** from `misode/mcmeta` data branch (version JSON identifies snapshot data version 5122). Each override contains only the intentional changes described above. This avoids copying old-worldgen formats into the 26.4 snapshot.

## Validation

```sh
python3 tests/test-hw-quiet-underground.py
python3 scripts/build-hw-quiet-underground.py
```

Tests check metadata, JSON registry shape, exact canyon probability, preserved regular cave probabilities, and the singular gated cheese-cave term. They do **not** replace live world-generation tests.
