# BuildCraft CML: unofficial CodaLoader port

Status: **BCCE 8.0.23 upstream baseline / 0.1.2-dev H.O.W.L. port, NOT a playable BuildCraft release.**
Target: **Minecraft Java 26.4 Snapshot 3** via native **CodaLoader**, without Forge/Fabric/NeoForge.

## Upstream and licensing

Selected source: [BuildCraft Community Edition 8.0.23](https://github.com/BCCE-team/BuildCraft),
pinned to `b1b166d29da797abf6df3e0618a3bd62f14bc41e`.
Use its shared API v2 and modern 1.21.11 source family as the porting reference.
The build imports original resources unchanged and includes the MPL-2.0
license and exact corresponding-source URL. Forge/NeoForge binaries are not
bundled or loaded. See [PORTING-SOURCES.md](PORTING-SOURCES.md).

This is an unofficial early H.O.W.L. adapter, not the complete BCCE mod.

## First native BuildCraft 8.0 transport preview (Nightly)

The native Minecraft 26.4 Snapshot 3 block/item registry bridge now adds a
**BuildCraft** Creative inventory tab containing:

- **Wooden Transport Pipe**: actual placeable BlockItem with original upstream art
- **Cobblestone Transport Pipe**: actual placeable BlockItem with original upstream art
- **Redstone Engine**: actual placeable BlockItem, powered by neighboring redstone
- **BuildCraft Wrench**: actual item with original upstream texture; interaction still pending

Original assets are imported unchanged from the pinned BCCE source and
adapted to Snapshot 3 JSON item/model definitions, not redrawn.

**Native transport smoke**: source single chest at `(0,64,0)`, wooden pipe
at `(1,64,0)`, cobblestone pipes at `(2,64,0)` and `(3,64,0)`,
destination empty chest at `(4,64,0)`. Place redstone engine at
`(1,65,0)`, adjacent to the wooden pipe. Power the engine with redstone.
The current server-thread integration attempts to transfer up to 16
original Minecraft ItemStack objects per 20 ticks, subject to capacity.
It verifies block IDs and loaded chunks before mutating real chests.
Optional diagnostic command:
`/buildcraft pulse 0 64 0 4 64 0`

**Important limitations:** This is the *first transport slice*, not BuildCraft
8.0 feature parity. The engine's original MJ temperature stages are not ported
yet; items do not animate through pipes; the wrench currently has no in-game
right-click behavior; powered engine placements are not yet restored after a
restart (place a fresh engine in the test world). Real chest-to-chest game
operation and Creative UI rendering still require a user playtest.
Use a throwaway Nightly world. Existing worlds are never migrated.

The old headless `PipeNetwork` tests remain as regression coverage, but no
longer define the target: the selected BCCE source is canonical.
Do not advertise parity until all original recipes, GUIs, pipe variants,
quarry, fluids and machinery have actually been ported.

## What exists now

- A valid `coda.mod.json` manifest and small native CodaLoader entrypoint.
- An opt-in `/buildcraft` status command (only when this mod is loaded).
- Minecraft-independent item-packet network: six-way adjacency, packet capacity, inventory sinks, deterministic round-robin junctions, inventory priority, single-hop ticks, backpressure, and no backwards ping-pong on dead ends.
- Chunk-load-aware ticking: refuses to move packets from or into unloaded positions. The Minecraft adapter must supply a server-thread loaded-chunk predicate.
- Lossless transport snapshot/restore with bounded packet and capacity validation, SHA-256 protected versioned saves, atomic file replacement, and corrupt-save rejection. Saving and loading never silently clears in-flight stacks.
- JVM tests cover routing, conservation, chunk-load boundaries, snapshots, checksum corruption, overflow rejection and atomic restart recovery.
- A development-only `BuildCraftTransportRuntime` now hooks H.O.W.L.'s native server-tick callback. A future Minecraft block adapter must explicitly attach a network and real loaded-chunk predicate for each **server session AND dimension**. The integrated single-player server can own Overworld, Nether and End simultaneously; pipe positions and saved cargo from different dimensions must never mix. Integration tests cover session/dimension isolation, tick/replay guards, off-thread rejection, and saved snapshots on individual dimension detach. **No playable pipe blocks or chest transactions exist yet.**
- **First wrench/upgrade prototype:** every simulated pipe can store a specific output direction or return to automatic routing, and optionally whitelist one item ID. Backpressure and filters retain cargo rather than dropping or rerouting it. These settings persist in **version 2** of the pipe save, while pre-upgrade version 1 saves remain readable with default settings. The wrench is a headless simulation operation only: no Minecraft item, UI, right-click hook or world block has been added.
- **Wooden-pipe pulse simulation:** virtual inventories can hold bounded stacks, and a deliberate `extractVirtualOnPulse` operation pulls up to 64 items from a face-adjacent inventory into a pipe. It rejects unloaded positions and full pipes, honors item whitelists, marks entry direction to prevent immediate bounce-back, splits stacks without duplication and survives v2 save/reload. **This is not a Minecraft chest/engine integration**: real inventories and MJ power must be handled through a future authoritative, transactional block adapter.

## First executable single-player chest test (EXPERIMENTAL)

H.O.W.L. now builds a **Nightly-channel-only ZIP** that CodaLauncher
downloads and installs into the isolated Nightly game profile's `minecraft/mods`.
The package contains the matching loader JAR and a transient BuildCraft
development payload. The in-game
`/buildcraft pulse <x1> <y1> <z1> <x2> <y2> <z2>` command verifies a straight
row of **1-16 vanilla glass blocks between two real loaded single chests or
barrels**, then uses the loader's integrated-server inventory API to move up to
16 complete Minecraft ItemStack objects into the destination.

- In CodaLauncher 0.7.6, select Settings -> Nightly, save, then Play Nightly.
  The launcher obtains the verified ZIP automatically. Place the chests and
  glass in a new throwaway world; no extra playtest launcher is needed.
- This is **real in-game chest mutation**, not our virtual PipeNetwork test.
  Every moved ItemStack retains its Minecraft components. It refuses unloaded
  chunks, missing glass, incompatible storage, full outputs, and wrong-thread use.
- The command itself stands in for an engine pulse. The glass blocks stand in
  for future wooden/transport pipes. No custom BuildCraft block, wrench,
  graphics, animations, or engine power item is included.
- Package: `bash scripts/build-buildcraft-playtest.sh` after
  `bash scripts/build-buildcraft-cml.sh` against a sibling updated H.O.W.L.
  checkout. CI creates a **development-only artifact**, not a player release.
- **Live Snapshot 3 verification is still required**. Failing named mappings
  must refuse the command instead of guessing another API or touching saves.
  Do not use this experiment in an existing world.

**Still a development-only build:** no original BuildCraft block is placed
in Minecraft yet. The Nightly opt-in delivery is enabled, but this is not a Stable release.
Official Stable auto-install remains disabled until genuine BuildCraft blocks
and live Snapshot 3 gameplay have been verified.

## Planned wooden-pipe gameplay

Original BuildCraft wooden pipes extracted items when supplied with engine energy or an appropriate pulse. Our headless test reproduces the **pulse-triggered transfer** and safe extraction behavior, rather than pretending that a wooden pipe passively vacuums nearby chests.

Next required pieces are the Snapshot 3 block/item registrations, wooden pipe placement and facing, an energy-pulse producer, an actual transactional vanilla inventory adapter and a server-thread save/reload test. The current virtual inventory is for deterministic simulation tests only; **never mirror a live chest while leaving its original items available**.

## Planned in-game wrench behavior

- Right-click a pipe with the future H.O.W.L. wrench to cycle output: auto, north, south, east, west, up, down, then auto again.
- A future pipe GUI will let players select an exact item whitelist, then clear it to accept all. This is intentionally a minimal filter, **not** BuildCraft's full diamond per-side sorting yet.
- Changes require a server-authoritative Minecraft interaction hook. An output aimed at a full container keeps items waiting safely. Invalid IDs and applying pipe controls to inventories are refused.
- The configuration is native H.O.W.L. gameplay inspired by original BuildCraft, not a direct copy of its source or textures.

## Real port milestones

1. **CodaLoader API**: version-pinned server-side block/item registry, block-entity lifecycle (create/load/save/remove, inventory adapters, chunk load/unload), main-thread tick scheduling, client model/resource registration. Fail closed on unknown snapshot mappings.
2. **Playable transport slice**: wooden item extraction and stone/cobblestone transport pipes, wrench, vanilla chest/hopper integration, saved in-flight stacks, item-drop recovery on break, orientable model and recipe. Verify two-chest test.
3. **Energy**: self-contained power grid and redstone engine, later Stirling/combustion engines. Decide MJ interoperability only after adapter design.
4. **Mining/building**: mining well, quarry, filler, builders/blueprints, with unloaded chunk and protection/permission checks.
5. **Fluids/logic**: fluid pipes, tanks, pumps, gates, filters, signals, graphics, sounds and localization.
6. **Ship**: client/server regression tests, world migration/backups, source attribution review, **required** CodaLauncher-managed install and pinned updates. H.O.W.L. has no optional first-party player mods.

## Constraints

- Never tick/mutate the world on the render thread; keep server authoritative.
- Never route into unloaded/unregistered locations or void items when breaking a pipe.
- One packet travels at most one connection per simulation tick; preserve item counts.
- CodaLoader supplies generic APIs. This mod supplies gameplay.

Run `./scripts/test-buildcraft-cml.sh` to test transport, unload safety and persistent state. Run `bash scripts/test-buildcraft-integration.sh` with `HW_CODALOADER_API_DIR` pointed at current H.O.W.L. API sources to test native tick integration. After adding block APIs, run `./scripts/build-buildcraft-cml.sh` with `HW_CODALOADER_API_DIR` set and verify live Snapshot 3 behavior.
