# H.O.W.L. Frontier 0.2 design

Frontier 0.2 is a rebuild from scratch. Saves, settlers and state files from
0.1.x are not migrated: 0.2 uses a new state file and ignores the old one
(it is never read, changed or deleted).

**The loop:** spawn in the wilderness → light a campfire → a pioneer arrives →
gather resources → build a cabin → recruit five workers → unlock a ring of land →
expand → build a village.

## Rules the code must keep

1. **Real items only.** Nothing is generated from nothing. Workers use, carry
   and deliver native Minecraft items.
2. **Missing is not dead.** An entity that cannot be found is *absent*. Only an
   observed death, or an explicit player action, ends a settler. Absence never
   frees a bed or spawns a replacement.
3. **No offline progress.** A camp whose campfire chunk is unloaded is paused.
   An absent settler does not get hungry or work.
4. **The territory is the only spatial rule.** Beds, the stockpile, work areas
   and arrival points must lie inside the settlement's territory. There are no
   separate block radii.
5. **Territory never shrinks.** It grows from the highest number of residents
   ever reached at once, and that peak is stored.
6. **One failure never stops the camp.** Each part of the update is contained;
   the loader must never see an exception from Frontier's tick.
7. **Unreadable state is never overwritten.** The camp pauses and explains how
   to restore it.
8. **Never touch player builds.** Reset and retire change only Frontier's own
   state file and its own settler entities.

## Territory

A settlement's territory is a square of chunks centred on the campfire's chunk.
Chunk coordinates use floor division, so block x = -1 is in chunk -1.

```
milestone   = floor(peakResidents / 5)     (stored; only ever increases)
side        = 3 + 2 × milestone            (chunks)
reach       = 1 + milestone                (chunks from the centre chunk)
```

| Peak residents | Territory | Chunks | Blocks |
| ---: | ---: | ---: | ---: |
| 0-4 | 3×3 | 9 | 48×48 |
| 5-9 | 5×5 | 25 | 80×80 |
| 10-14 | 7×7 | 49 | 112×112 |
| 15-19 | 9×9 | 81 | 144×144 |

The claim is **soft**: players may walk anywhere. Building, mining and
interacting outside the territory will be refused once CodaLoader offers
cancellable interaction events (L5 below). Settler activity is restricted to the
territory now.

## Settlers

Each settler has a persistent UUID, a unique name, a lifecycle state, hunger,
loyalty, a profession and an optional bed.

| State | Meaning | Holds a bed | Counts as resident |
| --- | --- | --- | --- |
| COMMITTED | Identity saved, entity not yet spawned | yes | no |
| ARRIVING | Walking in from the arrival point | yes | no |
| RESIDENT | Lives in the camp | yes (if any) | **yes** |
| LEAVING | Walking out after a food shortage | no | no |
| DEAD | Death observed | no | no |
| DEPARTED | Walked out and removed | no | no |
| RETIRED | Removed by the player (retire or reset) | no | no |

The last three are terminal. Illegal transitions are refused in code.

- **The pioneer** is the first arrival. It needs no bed and comes about
  60-120 seconds of loaded camp time after the campfire is lit.
- **Later arrivals** each need a free sheltered bed inside the territory and
  arrive at a steady interval.
- **A resident counts toward territory** from the moment it becomes RESIDENT.
  The pioneer counts as worker 1.
- **Beds** are found from bed placement events immediately, re-checked
  regularly, and also found by a slow background scan of the territory.

## Settlement records

Each settlement has an owner UUID. Until CodaLoader reports who placed a block
(L2), a camp started by placing a campfire has an unknown owner, which is set by
the first player to use a `/frontier` command inside the territory. The state
file can hold several settlements; this release creates one per world.

## Commands (until the campfire screen exists)

| Command | Purpose |
| --- | --- |
| `status` | Camp, territory, arrivals and why they are waiting, stockpile, problems |
| `settlers [all]` | Settlers; `all` includes dead, departed and retired |
| `stockpile` | Set the chest or barrel under the crosshair |
| `name <id> <username>` | Rename a settler; their skin follows the Minecraft account |
| `retire <id>` | Remove a settler who is gone (refused while they are alive and loaded) |
| `reset`, then `reset confirm` | Start over: dismiss all settlers and forget the camp |

Reset and retire never change world blocks or player inventories. Dismissed
settlers who are loaded leave with what they carry; absent ones are removed when
their chunk next loads, so they can never return as duplicates.

## Settlement planning

The settlement plans itself. The player can veto, but never has to place a plot.
Settlers only ever build what the planner has approved.

### The grid

The land is divided into **cells of 8x8 blocks**, aligned with chunks (2x2 cells per
chunk). Every cell gets one role from a survey of the real terrain:

| Role | Meaning |
| --- | --- |
| Square | The campfire's chunk. Never built on |
| Open | Flat, safe and empty: a possible plot or road |
| Forest | 3 or more natural trees: kept for lumberjacks; roads may cross it after felling |
| Farm | Farmland is already there |
| Yours | Blocks that are not natural (planks, cobblestone, glass, doors, chests...). Never touched |
| Water | Water in the cell |
| Hazard | Lava nearby, a drop over 4 blocks within 3 blocks, a hollow under the floor, or loose ground (sand, gravel) |
| Steep | The ground varies by more than 2 blocks. A road may cross it if it varies by 4 or less |
| Unknown | Not loaded yet, or not surveyed yet |

### Roads first

A plot is only used once a road connects it to the square. Roads are planned
automatically as the cheapest route over the grid from the square: they prefer flat
open cells, avoid forest, and never climb more than 1 block per 2 blocks walked. A
road never crosses water, hazards or your builds. A building's door faces its road.

### Choosing plots

When the settlement needs a building (for example, a new home for the next settler),
the planner picks the open cell with the best score: close to the square by road,
flat, and not next to hazards. A house sits in its 8x8 cell with a 1-block margin on
the road side, so buildings never touch.

### Materials follow the land

Each settlement builds in a material palette chosen from what its land offers:

| Land | Palette |
| --- | --- |
| 4 or more natural trees | Wood of the most common local tree: logs for the frame, planks for walls and roof once settlers can craft (L8); logs everywhere until then. Cobblestone foundation |
| Mostly sand or sandstone | Sandstone |
| Mostly terracotta or red sand | Terracotta, using the most common local colour for walls |
| Anything else | Cobblestone |

Blueprints name **roles** (frame, wall, floor, roof, door, bed) instead of blocks,
so one blueprint fits every palette. All palette blocks can be gathered by the jobs:
lumberjacks bring logs, and miners also dig sandstone and terracotta.

### Foundations (0.4.0)

Houses are dug into the ground, not laid on it. The planner's level for a plot is the
typical height of its ground surface. The builder digs out that top layer inside the
footprint (by hand, or with a pickaxe from the stockpile for stone) and lays the
blueprint's bottom layer there as the **foundation**: cobblestone under wood houses, the
house's own material under sandstone, terracotta and cobblestone houses. Walls stand on
the foundation, so the floor is level with the land around the house.

Before the foundation goes in, every column must rest on solid ground. Dips under it, up
to 3 blocks deep, are filled from the bottom with the earth dug out of the plot (or
cobblestone); a deeper hollow pauses the build with its position.

A builder short of cobblestone digs it: if a mine is set (`/frontier mine`) and there is
a pickaxe in the stockpile, they put their axe back, take the pickaxe and mine rock until
there is enough. Otherwise they say what is missing.

Projects saved by 0.3.x were laid out on top of the ground. If nothing of such a house
stands yet it simply switches to the sunk layout. If it was started, the builder first
takes it down, top first: only the blocks they placed for it (its own steps and the top
half of its door), never anything else. Everything goes back to the stockpile and is
used again. A bed already placed is left for the player to pick up, because settlers
cannot take beds down.

### The settlement's mine (0.4.1)

One shared mine per settlement, stored with the settlement, worked by every miner and by
builders who need rock. Only one settler digs at the face at a time; others wait their turn.

- **Chosen by the miners** when there is none: the cell centre of an open or steep cell in
  the land, not a road, the project's plot or its road, with room for 16 steps in a direction
  inside the land. Steep cells first, then nearest the stockpile. The planner keeps homes off
  the mouth and the top of the stairway.
- **Shape:** a stairway one block down per step for 8 steps, three blocks high on the way
  down, then a 2-high level tunnel to 32 steps. Dirt and grass come away by hand; stone needs
  a pickaxe.
- **Walking:** to the mouth first (the block in front of the first step), then down the dug
  steps three (or two) at a time, never one: the loader's pathfinder (accuracy 1) treats a
  target within a block as reached and does not move. Digging starts as soon as the face is
  within reach (0.4.2). Settlers inside the mine heading anywhere else walk back up the same
  way. This replaces aiming straight at the face, which led over the hill above it.
- **Safety:** water, lava, sand or gravel next to the next block, or a block miners do not
  dig, closes a chosen mine (players are told why) and a new place is chosen. A finished mine
  is closed the same way. A mine the player set (`/frontier mine`) is never closed by settlers;
  it pauses and says why, as before.

### Mountain homes and underground villages (later)

Some buildings are dug instead of built. A mountain home is carved into a slope that
is solid rock, and an underground village grows from a shaft. They follow one extra
rule: **every dug room keeps at least 2 solid blocks on every side**, except its own
entrance, so a dig never breaks through the far side of a mountain, into a cave, or
into water or lava. Before a dig starts, the planner checks the whole volume plus that
shell. Steep cells that are solid rock become candidates for mountain homes.

## CodaLoader contract (proposal, needs approval)

Each is a separate CodaLoader change with its own version bump, proposed before that
repository is touched. L6 shipped in H.O.W.L. 0.0.46 (`CodaLivingWorld.place` and
`shapePath`); the others do not exist yet.

| # | Hook | Frontier uses it for |
| --- | --- | --- |
| L1 | Settler removal event with a reason (killed, discarded, converted); never fired for unloading | Automatic death tracking |
| L2 | Placing player's UUID on block placement | Settlement owner |
| L3 | Player first-join event, teleport and respawn point | Starting locations |
| L4 | Biome and surface sampling for unloaded positions | Safe starting-location search |
| L5 | Cancellable break, place and use events | Soft-claim enforcement, campfire screen, giving tools to settlers |
| L6 | Settler places a block or bed from a real carried item (0.0.46) | Builders and cabins |
| L7 | Server particles or a client overlay | Visible border |
| L8 | Settler crafts at a crafting table (proposed for 0.0.47, below) | Planks, then plank houses; later doors, beds and tools |

### L8: crafting at a workbench (H.O.W.L. 0.0.47)

```java
/**
 * Craft {@code times} of one vanilla crafting-table recipe, as a player at that table would.
 * The settler must be loaded, alive and within 3 blocks of a minecraft:crafting_table at
 * {@code workbench}; that table must be within 3 blocks of the chest or barrel at {@code supplies}.
 * Ingredients come from the settler's own inventory first, then from the supplies; results and
 * any vanilla remainders (empty buckets...) go into the settler's inventory. Each craft is
 * all-or-nothing: if the ingredients or the room for the result are missing, that craft and every
 * later one change nothing. Returns how many crafts were done (0..times).
 * Only recipes of the vanilla crafting-table type are accepted; unknown ids throw.
 */
default int craft(UUID id, CodaBlockPos workbench, CodaBlockPos supplies, String recipeId, int times) throws Exception
```

As built: `workbench == null` crafts in the 2x2 grid a player carries (planks, sticks, a
crafting table). Frontier calls it by reflection, so the same JAR runs on 0.0.46 with
crafting off.

The loader looks the recipe up in the game's own recipe manager, so results always match
what a player would get, and no recipe data is copied into Frontier. Frontier then finds a
crafting table placed within 3 blocks of the stockpile, has the builder turn logs into
planks there when a plank house needs them, and switches wood palettes to log frames
with plank walls, roof and floor. Nothing is crafted without a real table and real items.

### A settler's multi-tool (idea, later)

One tool that works as an axe, pickaxe and shovel for settlers, like the Paxel idea from
other mods, so a builder does not walk back to swap tools. It needs a new item and its
recipe (native registry hooks already exist in the loader), a durability rule, and a
decision on whether players may use it too.

## Release plan

| Version | Contents | Needs |
| --- | --- | --- |
| 0.2.0-0.2.2 | Camp, territory and expansion, pioneer, recruitment, needs, lifecycle, reset and retire, styled chat | Nothing new |
| 0.2.3 | Lumberjack, farmer and miner jobs inside the territory, chosen from stockpile tools | Nothing new |
| 0.2.4 | Settlement planner: terrain survey, cell roles, roads, plot choice and material palette, shown with /frontier plan. Nothing is built yet | Nothing new |
| 0.3.0 | Builders: roads, then the starter hut on the planned plot, in the land's palette, from real stockpile items; door and bed from the stockpile | L6 (0.0.46) |
| 0.3.x | Death events, owner from placement, soft-claim enforcement, campfire screen | L1, L2, L5 |
| 0.4.0 | Houses dug into the ground on a foundation, dips filled, builders mine rock for it; 0.3.x huts taken down and rebuilt | Nothing new |
| 0.4.1 | One shared mine the miners choose and dig as a walkable stairway; walking in and out through its mouth | Nothing new |
| 0.4.2-0.4.3 | Miners keep digging; place/break speed settings; uncounted beds explained | Nothing new |
| 0.4.4 | Crafting: planks, sticks, a workbench placed by the stockpile, doors, beds from wool, ladders; plank houses. Off on older loaders | L8 (0.0.47) |
| 0.5.0 | Mountain homes and underground villages; expeditions | L6, plus an entity storage hook for expeditions |
| 0.6.0 | Starting locations, visible border | L3, L4, L7 |
