# H.O.W.L. Frontier 0.1.0-dev.1

A new settlement play mode for Minecraft Java 26.4 Snapshot 3 and H.O.W.L. 0.0.43+.
**Published for CodaLauncher 0.7.32+ on Windows Nightly.** This first milestone implements the camp and starter workers;
it does not yet implement the wider Deepworld, industry or ecology expansions.

## Delivery and world isolation

Player testing must use **CodaLauncher Nightly > Frontier > Play / Install-Repair**.
No manual JAR, ZIP or mod-folder installation is a supported testing path.
Frontier has its own `nightly/profiles/frontier/minecraft` directory. Classic and
Stable saves are not moved or converted. The mod only activates when the launcher
writes the Frontier profile marker. Existing base-loader utility mods are retained;
no old mod or repository is deleted by this project.

Use a new disposable Frontier world for the initial rendered playtest. To roll
back, close Minecraft and select Classic; its original profile remains in place.
A valuable Frontier world should be backed up as a complete closed save before
changing/removing gameplay mods. Settlement writes also retain the previous
`howl_frontier/settlement.properties` file beside the current state.

## First camp

1. Place an ordinary **lit campfire**. The first one establishes the camp. For an
   existing campfire, stand nearby and use `/frontier camp x y z`.
2. Build cabins and beds within 16 blocks horizontally and 6 vertically. Only bed
   heads with headroom and a solid roof 2-6 blocks overhead are counted. Bed scans
   are incremental and may take about a minute. Clear a walking route to each bed. Leave cabin doors open for this initial prototype; automatic door interactions still need client validation.
3. Mark a **single chest or barrel**: `/frontier stockpile x y z`. Stock it with
   bread, cooked food, carrots, beetroot or apples. Poisonous/raw food is excluded.
4. The first free bed attracts a traveller after about one minute of loaded camp
   time. Further travellers arrive at five-minute intervals. No food gate prevents
   arrival: beds determine capacity, while food determines whether residents stay.
5. `/frontier settlers` lists IDs, jobs, hunger, loyalty and current activity.

Starter camp capacity is 24. Existing claims remain allocated while a settler is
unloaded. Missing entity lookups are never treated as death or permission to spawn
replacements. A removed/unroofed bed is reassigned when another sheltered bed is
available. Use `/frontier ignorebed x y z` on your own bed's HEAD to reserve it for
yourself; `/frontier bed x y z` explicitly re-adds a valid head.

## Hunger and loyalty

Hunger decreases by one every ten seconds of loaded entity time. A real meal
restores 40, and good provision gradually improves loyalty. Low-loyalty residents
leave below 30 hunger; medium loyalty endures until 10. Loyalty of 80+ stays through
shortages and can die: at zero hunger the native entity takes starvation damage.
Unloaded entities pause; there is no offline or sleep-time catch-up. Departure
releases the bed immediately, then the settler physically walks back to the edge
of camp. A blocked route does not magically teleport or remove them.

## Starter workers

Use `/frontier job <id-prefix> lumberjack|miner|farmer|idle`, then
`/frontier work <id-prefix> x y z [north|south|east|west]`.
Each work area must be within 32 blocks of camp. Each job requires a reachable
stockpile. Tools come from that stockpile, wear normally and stay with the worker.
Changing tool type returns the old tool only when stockpile space is available.

- **Lumberjack:** mark the lowest log of a small, straight-trunk tree. Supply an
  axe and its sapling. The worker verifies a canopy, cuts up to six vertical logs,
  replants from real inventory, and delivers wood. Branched/tall trees and general
  forest zoning are later milestones. Mark a new tree after completion.
- **Miner:** mark the first feet-height block and choose the tunnel direction.
  Supply a pickaxe appropriate for the material. An eight-block, two-high starter
  tunnel is excavated head-first, with deliveries along the way. Liquid, falling
  material, unloaded boundaries and unsupported blocks pause work. It never digs
  a quarry, targets containers or invents ore drops.
- **Farmer:** prepare farmland and plant a 9x9 field; mark its centre at crop
  height. Workers harvest ripe wheat, carrots, potatoes or beetroot, replant with
  actual seeds/produce, and deliver the remainder. Native Minecraft grows crops;
  field creation and automatic planting of a completely empty field are later work.

Workers sleep in their assigned native beds at night. Hunger can interrupt work
and sleep. Full stockpiles pause deliveries. Starter controls use commands; a
right-click camp/worker screen is not part of this initial milestone.

## Development checks

`bash scripts/build-howl-frontier.sh` builds the Java 21 mod against matching loader
API sources and runs camp/survival/persistence scenarios. The compatible loader's
`scripts/test-living-world-native.sh` additionally uses a running, disposable
Mojang GameTest world to exercise native actors, navigation, food, tools, planting
and inventory delivery. Its integrated-server identity is a fixture, not a real
client. Rendered Nightly placement, sleeping and world save/reload remain separate
acceptance checks. Do not call automated tests a completed player playtest.
