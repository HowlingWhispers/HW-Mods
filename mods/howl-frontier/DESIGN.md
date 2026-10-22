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

## CodaLoader contract (proposal, needs approval)

None of these exist in H.O.W.L. 0.0.45. Each is a separate CodaLoader change
with its own version bump, proposed before that repository is touched.

| # | Hook | Frontier uses it for |
| --- | --- | --- |
| L1 | Settler removal event with a reason (killed, discarded, converted); never fired for unloading | Automatic death tracking |
| L2 | Placing player's UUID on block placement | Settlement owner |
| L3 | Player first-join event, teleport and respawn point | Starting locations |
| L4 | Biome and surface sampling for unloaded positions | Safe starting-location search |
| L5 | Cancellable break, place and use events | Soft-claim enforcement, campfire screen, giving tools to settlers |
| L6 | Settler places a block or bed from a real carried item | Builders and cabins |
| L7 | Server particles or a client overlay | Visible border |

## Release plan

| Version | Contents | Needs |
| --- | --- | --- |
| 0.2.0 | Camp, territory and expansion, pioneer, recruitment, needs, lifecycle, reset and retire, gathering jobs inside the territory | Nothing new |
| 0.3.0 | Death events, owner from placement, soft-claim enforcement, campfire screen | L1, L2, L5 |
| 0.4.0 | Builder and first cabin | L6 |
| 0.5.0 | Starting locations, visible border | L3, L4, L7 |
