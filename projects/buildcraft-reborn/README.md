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

## First genuine executable upstream regression

The original **BCCE `DelayedList`** scheduler used by
`PipeFlowItems`, and **`ItemTransportProfile`** API2 record now compile
unchanged in their original Java packages. A dedicated Java 21 test verifies
original delayed ordering, exactly-once completion, empty/negative delays,
concurrent queue behavior, and original item-profile validation:

```bash
bash projects/buildcraft-reborn/scripts/prepare-source.sh
bash projects/buildcraft-reborn/scripts/test-original-flow-core.sh
```

This is **original executable BuildCraft library code**, not a custom
transport engine or player-ready pipe. It doesn't yet create block entities,
move real Minecraft ItemStacks, consume MJ or render animations. Compilation
of `TilePipeHolder` and `BlockPipeHolder` is still pending their NeoForge
and Minecraft 26.4 compatibility adapters.

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

## Original item-port compatibility check

The original `buildcraft.api.v2.item.ItemPort`, `ItemMatcher`,
`ItemTransferResult`, `ItemTransferPolicy`, and `OperationMode` are
now vendored unchanged. CI compiles these exact BCCE sources against the
**SHA-1-verified Mojang Minecraft 26.4 Snapshot 3 client JAR**, using Java
25. It checks that the interfaces still reference Minecraft's real
`ItemStack`, not a mock or custom inventory transfer type:

```bash
bash projects/buildcraft-reborn/scripts/prepare-source.sh
bash projects/buildcraft-reborn/scripts/test-original-item-api.sh
```

A successful compile proves only the **item-transfer interface ABI** can be
used on this Minecraft target. It does not establish that pipe block entities,
NeoForge capabilities, world save/reload, energy receivers, renderer or
inventory transactions work yet. No mod JAR is produced.

## Original pipe holders: first reversible source adaptation

The port now stages **the actual 8.0.23 `BlockPipeHolder` and
`TilePipeHolder` Java files**, with just three tightly scoped,
reversible changes:

1. Original block constructor accepts the Mojang Snapshot 3
   registry-keyed `BlockBehaviour.Properties`. Original geometry,
   collision, waterlogging and on-interact methods remain unchanged.
2. Original `BlockPipeHolder.getTicker()` resolves the port's own native
   `hw_buildcraft_reborn:pipe_holder` type rather than NeoForge registry
   objects, retaining its call to `TilePipeHolder.update()`.
3. Original `TilePipeHolder` constructor resolves that same native type,
   preserving all original pipe state, NBT persistence, pluggables, wires,
   `PipeFlowItems` and other lifecycle logic.

```bash
python3 projects/buildcraft-reborn/scripts/stage-original-pipe-holders.py
bash projects/buildcraft-reborn/scripts/probe-original-pipe-compilation.sh
```

An isolated compiler probe attempts to build both staged original BCCE
classes with Minecraft's actual Snapshot 3 classes and BCCE's layered source.
If NeoForge/BuildCraft library dependencies are unresolved, CI produces
`pipe-holder-compiler-report.txt` with **NOT_COMPILED**, along with the
exact javac error log. **A green diagnostic workflow is not a green mod
compilation**. This project remains non-installable until the original
classes compile and load.

No old custom pipe gameplay, fake `BlockEntity` payload or released mod
jar is present.

## H.O.W.L. entrypoint wired to original BCCE constructors (source only)

The exact registration source now lives at
`bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java`.
It registers a **single original `BlockPipeHolder`** with the new ID
`hw_buildcraft_reborn:pipe_holder`, passes Mojang's keyed block
Properties into that original constructor, and supplies the actual
`TilePipeHolder` constructor as the native block-entity factory.
It intentionally does **not** register the H.O.W.L. generic tick callback:
the original BCCE `BlockPipeHolder.getTicker` already calls
`TilePipeHolder.update`.

The original BCCE ported classes are **not yet compiling**. At the
last verified probe the remaining failures include unported NeoForge
capabilities, model-data support and the removed Minecraft
`SoundType` API, plus missing annotation dependencies. The bridge
is therefore source only and has **no mod metadata**, launcher
installation, or release. A native registry bridge test alone is
not evidence of working BCCE gameplay.
