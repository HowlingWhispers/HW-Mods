# H.O.W.L. Frontier 0.2

A settlement play mode for Minecraft Java 26.4 Snapshot 3 and H.O.W.L. 0.0.45+.
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

Jobs (gathering, mining, farming) and building are being rebuilt; see DESIGN.md.

## Commands

| Command | What it does |
| --- | --- |
| `/frontier status` | Land, settlers, what arrivals are waiting for, stockpile, any problem |
| `/frontier settlers [all]` | Settlers with short IDs; `all` includes dead, departed and retired |
| `/frontier stockpile` | Set the chest or barrel you are looking at |
| `/frontier keepbed` | Keep the bed you are looking at for yourself (again to share it) |
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
