# H.O.W.L. Sorting Pipes 1.0.4

Original item-sorting mod for H.O.W.L. (HW-CodaLoader), exactly Minecraft Java
26.4 Snapshot 3. Requires H.O.W.L. 0.0.41 or newer, with the item-filtering transport API. Run the game with Java 25+.
The mod and loader compile for Java 21. No Forge or Fabric is involved.

## Installation

1. Open CodaLauncher **Nightly** and use Play / Install-Repair. The launcher verifies
   the H.O.W.L. runtime (0.0.41 or newer) and installs this required mod automatically.
   No separate download or manual JAR placement is needed.
2. Start the Nightly profile on 26.4 Snapshot 3. H.O.W.L. reads the mod's declared
   resource pack, installs and enables it automatically before Minecraft's
   first reload. The Transport Pipe core uses the original Pixel Studio blue-metal PNG;
   its connecting arms and all other blocks retain vanilla textures. No manual
   resource-pack selection is required. Creative inventory has native page
   arrows above the tabs; Sorting Pipes appears on the first mod page inside
   the normal tab grid. Search and player inventory remain accessible there.
4. Recipes, block drops and the written guide are declared server data in
   the mod JAR. H.O.W.L. automatically discovers and selects this data beside
   vanilla for both new and existing worlds. No manual data-pack install is
   required. `HOWL-Sorting-Pipes-Recipes.zip` remains available as a standalone
   copy for inspection; installing a second copy is unnecessary.
5. Find the five blocks in the **H.O.W.L. Sorting Pipes** creative tab.

## Written guide and advancement unlock

After **Acquire Hardware** (`minecraft:story/smelt_iron`) AND **Isn't It Iron
Pick** (`minecraft:story/iron_tools`) are complete, each player receives one
ordinary written **Sorting Pipes Guide**. It covers a first chest-to-chest
line, input direction, filters, attachments, all five crafting recipes and
troubleshooting across 21 short pages. The five recipes are also unlocked
in Minecraft's recipe book. Players who earned both advancements before
installing this release qualify on their next game tick. The hidden guide
advancement records the reward per player/world, so ordinary reloads and
rejoins do not repeatedly give books. Vanilla loot-reward delivery handles a
full inventory by dropping the reward nearby; keep an inventory slot free.

Minecraft's advancement trees are preserved. Seeing only Husbandry can be
normal early in a world: eating unlocks Husbandry, and having a crafting
table in your inventory unlocks the main Minecraft tab. Nether, End and
Adventure have their own progression triggers. The guide does not grant
vanilla milestones or replace their roots. If a guide is lost, with commands
enabled an operator can give a replacement using
`/loot give @s loot howl_sorting_pipes:guide`.

The first release uses the loader's integrated singleplayer storage bridge:
ordinary vanilla chest and barrel block entities only. Dedicated servers,
hoppers, modded machines, and sided storage contracts are not supported.
Each half of a double chest is treated as a separate physical inventory; use
single chests or barrels for predictable endpoint stock counts.

## Blocks and attachments

In-game help is split into short pages:

```
/howlpipes help setup
/howlpipes help filters
/howlpipes help attachments
```

There is currently no right-click settings screen or mode-cycling tool.
Whitelist means **only these items may pass**; blacklist means **these items
may not pass**. Use F3's targeted-block coordinates in the filter command.
The coordinates always name the pipe being configured, not its chest.

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
An attachment must touch one of the extractor's six faces, not a diagonal
and not another transport pipe. Placing it activates it automatically;
removing it returns to the first available matching output.

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

Automated checks cover native data-pack discovery, full Mojang registry loading,
vanilla advancement preservation, both-prerequisite predicates, persisted
reward completion, written-book components and native font wrapping. A rendered in-game placement/crafting/breaking and
save/reload playtest is still needed. Automated checks do not establish GUI,
resource-pack rendering or full world lifecycle correctness.

H.O.W.L. 0.0.40 fixed the native pipe outline: Snapshot 3 measures PipeBlock's
constructor width in model pixels. The selection shape now matches the six
pixel body and its connected arms, so aiming at the visible pipe can target
mining and pick-block. Headless checks test all 64 connection states for each
pipe, ray targeting, non-collision behavior and normal mining hardness.

## Source and notices

The new mod code and model generator are MIT licensed. Models reference
Minecraft's built-in textures; no Minecraft or BuildCraft artwork is copied
into the mod. Existing loader code retains its repository ownership and
notices, including its bundled ASM notice. This is an original H.O.W.L. mod,
not a BuildCraft port.
