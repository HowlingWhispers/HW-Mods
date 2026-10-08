# BuildCraft CML: unofficial CodaLoader port

Status: **0.1.0-dev foundation only, NOT a playable BuildCraft release.**
Target: **Minecraft Java 26.4 Snapshot 3** via native **CodaLoader**, without Forge/Fabric/NeoForge.

## Upstream and licensing

**Canonical BuildCraft source reference (user-selected):** [BuildCraft/BuildCraft](https://github.com/BuildCraft/BuildCraft). Start from this repository for the original gameplay, pipes, machines, recipes and licensing history, particularly the `8.0.x-1.12.2` branch.

**Secondary modern implementation reference:** [BuildCraft Refabricated](https://github.com/fromdisposition/BuildCraftRefabricated). Consult it where helpful for newer Minecraft implementation ideas, but it does **not** replace the original source link and its Fabric dependencies are not part of H.O.W.L. H.O.W.L. keeps its own block, inventory, lifecycle and rendering APIs. See [PORTING-SOURCES.md](PORTING-SOURCES.md).

Original BuildCraft copyright belongs to SpaceToad and BuildCraft contributors. BuildCraft Refabricated has its own contributors. The H.O.W.L. port is not endorsed by either upstream project.

BuildCraft's 1.12.2-and-later source is generally MPL 2.0 (`LICENSE-NEW`); older code can be MMPL 1.0.1 (`LICENSE`). Check individual file notices and history before importing any source, assets, models or textures. Preserve notices, publish modified MPL files under MPL 2.0, and provide full corresponding source code for distributed binaries. Use old-MMPL material only after a separate licensing review. **No original BuildCraft code or assets have been copied in this milestone.** The transport prototype is newly written.

## What exists now

- A valid `coda.mod.json` manifest and small native CodaLoader entrypoint.
- An opt-in `/buildcraft` status command (only when this mod is loaded).
- Minecraft-independent item-packet network: six-way adjacency, packet capacity, inventory sinks, deterministic round-robin junctions, inventory priority, single-hop ticks, backpressure, and no backwards ping-pong on dead ends.
- Chunk-load-aware ticking: refuses to move packets from or into unloaded positions. The Minecraft adapter must supply a server-thread loaded-chunk predicate.
- Lossless transport snapshot/restore with bounded packet and capacity validation, SHA-256 protected versioned saves, atomic file replacement, and corrupt-save rejection. Saving and loading never silently clears in-flight stacks.
- JVM tests cover routing, conservation, chunk-load boundaries, snapshots, checksum corruption, overflow rejection and atomic restart recovery.
- A development-only `BuildCraftTransportRuntime` now hooks H.O.W.L.'s native server-tick callback. A future Minecraft block adapter must explicitly attach the per-server pipe network and real loaded-chunk predicate. Integration tests cover server session isolation, tick/replay guards, off-thread rejection, and save snapshots on detach. **No playable pipe blocks or chests exist yet.**
- **First wrench/upgrade prototype:** every simulated pipe can store a specific output direction or return to automatic routing, and optionally whitelist one item ID. Backpressure and filters retain cargo rather than dropping or rerouting it. These settings persist in **version 2** of the pipe save, while pre-upgrade version 1 saves remain readable with default settings. The wrench is a headless simulation operation only: no Minecraft item, UI, right-click hook or world block has been added.
- **Wooden-pipe pulse simulation:** virtual inventories can hold bounded stacks, and a deliberate `extractVirtualOnPulse` operation pulls up to 64 items from a face-adjacent inventory into a pipe. It rejects unloaded positions and full pipes, honors item whitelists, marks entry direction to prevent immediate bounce-back, splits stacks without duplication and survives v2 save/reload. **This is not a Minecraft chest/engine integration**: real inventories and MJ power must be handled through a future authoritative, transactional block adapter.

**Still a development-only build:** nothing currently places a BuildCraft block in Minecraft. No player distribution or launcher install is enabled until the pipes are playable and tested in Snapshot 3.

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
