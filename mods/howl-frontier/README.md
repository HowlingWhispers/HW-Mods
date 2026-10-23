# H.O.W.L. Frontier 0.4

A settlement play mode for Minecraft Java 26.4 Snapshot 3 and H.O.W.L. 0.0.46+.
0.2 is a rebuild from scratch; see [DESIGN.md](DESIGN.md) for the rules and the plan.

## Delivery and world isolation

Play and test through **CodaLauncher Nightly > Frontier > Play / Install-Repair** only.
No manual JAR, ZIP or mod-folder installation is supported. Frontier runs only in its
own `nightly/profiles/frontier/minecraft` profile, when the launcher writes the Frontier
play-mode marker. Classic and Stable saves are never touched.

Use a new world. 0.1.x settlements are not carried over: their old
`howl_frontier/settlement.properties` file is ignored and left in place.

## Playing

1. **Found a settlement.** Place and light an ordinary campfire. Your land is the 3x3
   chunks around it. (Or look at a lit campfire and use `/frontier camp`.)
2. **The pioneer** arrives within about two minutes of play and needs no bed.
3. **Set a stockpile.** Look at a single chest or a barrel inside your land and use
   `/frontier stockpile`. Keep bread, cooked food, carrots, beetroot or apples in it.
4. **Build beds.** Each later settler needs a free bed inside your land with a solid
   roof 2-6 blocks above its head. A bed that does not count is reported when you place it, and
   `/frontier status` lists it with the reason (no roof, something right above the pillow). Placed beds count at once; older ones are found by
   a slow background scan. Settlers arrive at five-minute intervals.
5. **Grow.** Every 5 residents at once unlock one more ring of chunks: 3x3, 5x5, 7x7...
   Land never shrinks.

Hunger drops by one point every ten seconds of play. A meal restores 40. Less loyal
settlers leave during a shortage; loyal ones stay and can starve. If nobody is left,
a new pioneer comes. Nothing happens while the settlement's area is not loaded.

## Jobs

Residents pick a job from the tools in the stockpile and fetch the tool themselves:

| Tool in the stockpile | Job | What they do |
| --- | --- | --- |
| Axe | Lumberjack | Cut natural trees in your land (up to 6 logs tall), replant a sapling from the stockpile |
| Hoe (with crops on farmland) | Farmer | Harvest ripe wheat, carrots, potatoes and beetroot, replant with real seeds |
| Pickaxe | Miner | Work the settlement's one shared mine: a stairway the miners choose and dig themselves |

**The mine.** There is one mine for the whole settlement, and every miner works in it,
one at the face at a time. The first miner chooses the spot: open or hilly ground inside your
land, away from homes, roads and fields, near the stockpile (hillsides first). They dig a
stairway: one block down per step for 8 steps, three blocks high so everyone can walk it,
then level for the rest of its 32 steps. Settlers always walk in through its mouth and back
out the same way, never over the hill above it. If the stairway reaches water, lava, sand,
gravel or something miners do not dig, it is closed safely and they choose another place;
when it is finished, they choose a new one. Prefer a spot of your own? Look at it, face
the direction to dig and use `/frontier mine` (a straight 2-high tunnel that must be
reachable on foot); `/frontier mine auto` hands the choice back to the miners.

Work happens in daylight, only inside your land, and only with real items. Lumberjacks never
cut log houses or trees with player-placed leaves. Miners stop at water, lava, sand, gravel,
the edge of your land, and anything that is not stone, dirt or ore. Workers unload when they
carry a lot; a full stockpile is reported and nothing is lost.

## The settlement plan

Frontier surveys your land in cells of 8x8 blocks and plans the settlement itself:
where homes go, which roads lead to them, and what they are built from. See
[DESIGN.md](DESIGN.md) for the full rules. `/frontier plan` shows the plan as a map,
and `/frontier plan here` explains the spot you look at.

## Building

When every settler has a bed and the survey is complete, the next home starts on the
planned plot. A resident without a tool job becomes the builder, the pioneer first.

1. **Road.** With a shovel from the stockpile the builder turns grass and dirt into paths
   along the planned road, and cuts natural trees in the way with an axe from the stockpile.
2. **Clear**, always in this order, shown as (1/3), (2/3), (3/3) in `/frontier settlers`:
   1. fetch an axe and fell every natural tree that stands on the plot or hangs its leaves
      over it, whole and from the top, nearest first;
   2. clear the remaining leaves and plants by hand, top down;
   3. dig out the top layer of ground inside the walls: by hand, or with a pickaxe from the
      stockpile for stone.
3. **Foundation.** Dips under the plot are filled with the dug-out earth, then the
   foundation goes into the dug-out layer, level with the land around it.
4. **Build.** The starter hut (5x5, door facing the road, bed inside) goes up on the foundation.

Materials follow the land: where trees grow, a **plank house** on a cobblestone foundation
(log corner posts, plank walls and roof; with H.O.W.L. 0.0.47+), or a log cabin on older
H.O.W.L.; otherwise sandstone, terracotta or cobblestone throughout.
The builder takes them from the stockpile, fells trees for logs itself when it has an axe,
and digs rock in the settlement's mine for cobblestone when there is a pickaxe in the
stockpile. Doors and beds are crafted at the workbench (H.O.W.L. 0.0.47+; see Crafting);
on older H.O.W.L., put a door and a bed in the stockpile.

A hut started by Frontier 0.3.x sat on top of the grass. After updating, its builder takes
down what they built of it, puts everything back in the stockpile and builds it again on a
foundation. A bed that was already placed is yours to pick up first. Builders never break or replace anything you built or placed; they stop and say
what is in the way. `/frontier status` shows the Building line. Progress is saved after
every block.

## Crafting (H.O.W.L. 0.0.47+)

Settlers craft with the game's own recipes and real items:

- **Planks** from logs, and **sticks** and a **crafting table** from planks, need no table.
- Doors, beds and ladders need a **workbench**: a crafting table within 3 blocks of the
  stockpile. If there is none, a settler makes one from 4 planks and sets it up beside the
  stockpile.
- A builder short of planks or a door makes them, felling trees for logs first when there is
  an axe in the stockpile. A **bed** is made from **3 wool of one colour** and 3 planks; without
  wool the builder asks for a bed or wool.
- Everything crafted goes into the stockpile first, like any other delivery.

On H.O.W.L. 0.0.46 nothing is crafted: houses are log cabins and builders ask for doors and
beds, as before. Frontier switches crafting on by itself when the loader has it.

## Settings

`config/howl_frontier/howl-frontier.properties` in the Frontier profile (created on first start, never
overwritten). Changes apply the next time a world is opened.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `place_per_second` | 1 | 0.25 - 8 | Blocks a settler places per second while building |
| `break_per_second` | 1 | 0.25 - 8 | Blocks a settler breaks per second: clearing, taking down, mining, felling |

The startup log shows the pace in use. A settler who waited never catches up in one burst:
at most one second's worth of actions is saved.

## Commands

| Command | What it does |
| --- | --- |
| `/frontier status` | Land, settlers, what arrivals are waiting for, stockpile, any problem |
| `/frontier settlers [all]` | Settlers with short IDs; `all` includes dead, departed and retired |
| `/frontier stockpile` | Set the chest or barrel you are looking at |
| `/frontier keepbed` | Keep the bed you are looking at for yourself (again to share it) |
| `/frontier job <id> <lumberjack\|farmer\|miner\|labourer>` | Choose a settler's job; `labourer` lets them pick |
| `/frontier mine` | Optional: set the mine yourself at the block you look at, tunnelling the way you face |
| `/frontier mine auto` | Let the miners choose the settlement's mine (the default) |
| `/frontier plan [here]` | Show the settlement plan, or explain the spot you look at |
| `/frontier name <id> <username>` | Rename a settler; their skin follows that Minecraft account |
| `/frontier retire <id>` | Remove a settler who is lost (refused while they are alive and nearby) |
| `/frontier reset`, then `reset confirm` | Start over. Settlers leave; your buildings and items stay |

The first player to use a command inside an unclaimed settlement's land becomes its owner.

## When something goes wrong

A failure in one settler or system is contained: the rest keeps running, and
`/frontier status` says what failed. If `howl_frontier/frontier.properties` cannot be
read, the settlement pauses and the file is **never** overwritten. Close the world,
fix or restore it (the previous version is `frontier.previous.properties`), and reopen.

## Development checks

`bash scripts/build-howl-frontier.sh` compiles against the matching CodaLoader API and
runs every `*Test` class: core rules, persistence, the simulation and the commands, in a
mocked world. CI also runs CodaLoader's native GameTest against real Snapshot 3 code.
Neither is a rendered playtest; that happens in Nightly.
