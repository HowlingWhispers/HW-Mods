# H.O.W.L. Frontier 0.3

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
   roof 2-6 blocks above its head. Placed beds count at once; older ones are found by
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
| Pickaxe (after `/frontier mine`) | Miner | Dig a 2-high tunnel up to 32 blocks from where you set the mine |

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
   3. level the ground inside the walls by hand.
3. **Build.** The starter hut (5x5, door facing the road, bed inside) goes up from the floor.

Materials follow the land: a log cabin where trees grow (78 logs), otherwise sandstone,
terracotta or cobblestone. The builder takes them from the stockpile, and fells trees for
logs itself when it has an axe. **Put a door and a bed in the stockpile**: Frontier cannot
craft yet. Builders never break or replace anything you built or placed; they stop and say
what is in the way. `/frontier status` shows the Building line. Progress is saved after
every block.

## Commands

| Command | What it does |
| --- | --- |
| `/frontier status` | Land, settlers, what arrivals are waiting for, stockpile, any problem |
| `/frontier settlers [all]` | Settlers with short IDs; `all` includes dead, departed and retired |
| `/frontier stockpile` | Set the chest or barrel you are looking at |
| `/frontier keepbed` | Keep the bed you are looking at for yourself (again to share it) |
| `/frontier job <id> <lumberjack\|farmer\|miner\|labourer>` | Choose a settler's job; `labourer` lets them pick |
| `/frontier mine` | Start a mine at the block you look at, tunnelling the way you face |
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
