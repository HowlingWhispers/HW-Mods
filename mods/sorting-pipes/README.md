# H.O.W.L. Sorting Pipes 1.0.0

Original item-sorting mod for H.O.W.L. (HW-CodaLoader), exactly Minecraft Java
26.4 Snapshot 3. Requires H.O.W.L. 0.0.38 or newer, with the item-filtering transport API. Run the game with Java 25+.
The mod and loader compile for Java 21. No Forge or Fabric is involved.

## Installation

1. Update H.O.W.L. to 0.0.38 or newer through CodaLauncher.
2. Put `howl-sorting-pipes-1.0.0.jar` into the active H.O.W.L. Minecraft profile's
   `mods` folder. Do not place the compile-only API JAR in `mods`.
3. Start the profile on 26.4 Snapshot 3. The mod installs its model pack in the
   active game's `resourcepacks` folder. Enable **HOWL-Sorting-Pipes-1.0.0** in
   Options > Resource Packs. The supplied `HOWL-Sorting-Pipes-Resources.zip` is
   also a manual copy of that pack. Use one copy, not both.
4. For survival recipes and block drops, put `HOWL-Sorting-Pipes-Recipes.zip`
   into the test world's `datapacks` folder before creating/loading it. In an
   existing world with commands enabled, run `/reload`, check `/datapack list`,
   and enable the pack if necessary. This pack is required for normal block
   drops; the mod does not silently modify existing world data packs.
5. Find the five blocks in the **H.O.W.L. Sorting Pipes** creative tab.

The first release uses the loader's integrated singleplayer storage bridge:
ordinary vanilla chest and barrel block entities only. Dedicated servers,
hoppers, modded machines, and sided storage contracts are not supported.
Each half of a double chest is treated as a separate physical inventory; use
single chests or barrels for predictable endpoint stock counts.

## Blocks and attachments

| Block | Purpose |
|---|---|
| Extractor Pipe | Pulls one item from its selected adjacent input chest/barrel per pulse. No engine is needed. |
| Transport Pipe | Carries routes between extractors and output storage. |
| Sorting Pipe | An item-filter gate on a branch. Configure whitelist or blacklist. |
| Round Robin | Place next to an extractor to rotate successful deliveries among reachable outputs. Full or incompatible outputs are skipped. |
| Equalizer | Place next to an extractor to prioritize the output with the lowest count of the item currently being delivered. Rotates ties. |

Copper marks round-robin, gold marks the equalizer, diamond marks sorting,
oak marks extraction, and iron marks transport. Attachments occupy adjacent
block spaces; they are not face-mounted covers. Place at most one distribution
policy beside each extractor. Both policies together pause that extractor.

Round-robin rotates **individual items**, not full stacks. The equalizer
balances incoming distribution by exact item registry ID. It does not pull
existing items out of output chests to rebalance them and does not compare
whole-inventory fullness. Names/enchantments do not change its item-ID count,
but Minecraft's component matching still determines whether stacks may merge.

## Example

Place an input chest at `0 64 0`, an extractor at `1 64 0`, and transport pipes
at `2 64 0` and `3 64 0`. Branch with sorting pipes at `3 64 1` and `3 64 -1`.
Place output chests at `4 64 1` and `4 64 -1`.

```
/howlpipes input 1 64 0 west
/howlpipes filter 3 64 1 whitelist minecraft:iron_ingot
/howlpipes filter 3 64 -1 whitelist minecraft:gold_ingot
```

Iron goes to the first branch; gold goes to the second. Add a round-robin or
an equalizer next to the extractor to distribute among multiple matching
outputs. If a route crosses several filtered pipes, the item must satisfy
all of them. A filter on any pipe, including the extractor, is supported.

```
/howlpipes help
/howlpipes status
/howlpipes show 3 64 1
/howlpipes filter 3 64 1 blacklist minecraft:cobblestone,minecraft:dirt
/howlpipes clear 3 64 1
/howlpipes input 1 64 0 auto
```

Filters accept exact, namespaced IDs (up to 64 entries); no tags or wildcard
patterns. Unconfigured/cleared filters pass all items. Input `auto` operates
only when exactly one supported inventory touches the extractor; choose an
explicit direction if there are several. Commands require the pipe to be
loaded and within 64 blocks of the player, and operate in the player's current
dimension. Settings live in the world save at
`howl_sorting_pipes/settings.properties`, keyed by dimension and position.
They survive restart and remain associated with that coordinate if a pipe
is replaced. Distribution cursors reset between server sessions.

## Operating limits

Transfers execute on the native server thread, retain complete ItemStacks,
respect compatible destination stacks and inventory limits, and attempt
rollback on write failure. Only already-loaded blocks are inspected. The
mod never generates or loads chunks. Removed/unloaded routes cannot deliver.

A scan runs every 8 ticks, with at least 16 ticks between successful transfers
per extractor (at nominal 20 TPS, at most 1.25 items/second). Each scan processes
up to 16 extractors, rotates the work budget, and each extractor tries up to
four source item types. Route search is bounded to 128 pipe nodes and 32 hops.
Over-budget networks can run more slowly. Items remain in source storage until
delivery; there are no item sprites, transient cargo inventories, or animated
in-flight packets. Thin pipes use the loader's non-colliding native PipeBlock
connection support.

## Build and test

From this directory, with JDK 21+ and Python 3:

```
bash build.sh
bash test.sh
```

On Windows PowerShell:

```
./build.ps1
```

The generated `lib/howl-api-sorting-pipes.jar` is compile-only and is excluded
from the mod JAR. To compile against another matching API, pass its path as
the first build argument. Use `HW_CODALOADER_API_DIR` to select a matching loader checkout, or pass an API JAR.
The loader and mod each have their own build/release pipeline.

The standalone tests cover filters, branch routing, cycles, output capacity,
round-robin, equalizer stock balancing, session boundaries, source selection,
world/dimension persistence, conservation and configuration commands. The
loader fixture suite also tests filtered transfers and rollback.
The loader repository's `scripts/test-sorting-pipes-native.sh CLIENT_JAR LIBRARY_DIRECTORY` runs the
headless native check on checksum-pinned Mojang Snapshot 3 classes, with Java
25. That check uses actual registered blocks, connection states, real chest
entities and ItemStacks through the production transfer adapter, inside a
small test level/chunk shell. It supplies test component defaults because a
running game's data-pack loading is absent in this headless check.

All automated tests pass. A rendered in-game placement/crafting/breaking and
save/reload playtest is still needed. Automated checks do not establish GUI,
resource-pack rendering or full world lifecycle correctness.

## Source and notices

The new mod code and model generator are MIT licensed. Models reference
Minecraft's built-in textures; no Minecraft or BuildCraft artwork is copied
into the mod. Existing loader code retains its repository ownership and
notices, including its bundled ASM notice. This is an original H.O.W.L. mod,
not a BuildCraft port.
