# BuildCraft CML: unofficial CodaLoader port

Status: **0.1.0-dev foundation only, NOT a playable BuildCraft release.**
Target: **Minecraft Java 26.4 Snapshot 3** via native **CodaLoader**, without Forge/Fabric/NeoForge.

## Upstream and licensing

Upstream reference: https://github.com/BuildCraft/BuildCraft, chiefly the `8.0.x-1.12.2` branch.
Original copyright belongs to SpaceToad and BuildCraft contributors. This project is not endorsed by or affiliated with the BuildCraft team.

BuildCraft's 1.12.2-and-later source is generally MPL 2.0 (`LICENSE-NEW`); older code can be MMPL 1.0.1 (`LICENSE`). Check individual file notices and history before importing any source, assets, models or textures. Preserve notices, publish modified MPL files under MPL 2.0, and provide full corresponding source code for distributed binaries. Use old-MMPL material only after a separate licensing review. **No original BuildCraft code or assets have been copied in this milestone.** The transport prototype is newly written.

## What exists now

- A valid `coda.mod.json` manifest and small native CodaLoader entrypoint.
- An opt-in `/buildcraft` status command (only when this mod is loaded).
- Minecraft-independent item-packet network: six-way adjacency, packet capacity, inventory sinks, deterministic round-robin junctions, inventory priority, single-hop ticks, backpressure, and no backwards ping-pong on dead ends.
- Six JVM checks covering flows, backpressure, junction routing, invalid operations, sink priority, and dead ends.

**Nothing currently places a BuildCraft block in Minecraft. No automatic distribution or launcher install is enabled.**

## Real port milestones

1. **CodaLoader API**: version-pinned server-side block/item registry, block-entity lifecycle (create/load/save/remove, inventory adapters, chunk load/unload), main-thread tick scheduling, client model/resource registration. Fail closed on unknown snapshot mappings.
2. **Playable transport slice**: wooden item extraction and stone/cobblestone transport pipes, wrench, vanilla chest/hopper integration, saved in-flight stacks, item-drop recovery on break, orientable model and recipe. Verify two-chest test.
3. **Energy**: self-contained power grid and redstone engine, later Stirling/combustion engines. Decide MJ interoperability only after adapter design.
4. **Mining/building**: mining well, quarry, filler, builders/blueprints, with unloaded chunk and protection/permission checks.
5. **Fluids/logic**: fluid pipes, tanks, pumps, gates, filters, signals, graphics, sounds and localization.
6. **Ship**: client/server regression tests, world migration/backups, source attribution review, *optional* CodaLauncher install and pinned updates.

## Constraints

- Never tick/mutate the world on the render thread; keep server authoritative.
- Never route into unloaded/unregistered locations or void items when breaking a pipe.
- One packet travels at most one connection per simulation tick; preserve item counts.
- CodaLoader supplies generic APIs. This mod supplies gameplay.

Run `./scripts/test-buildcraft-cml.sh` to test the transport core. After adding block APIs, run `./scripts/build-buildcraft-cml.sh` with `HW_CODALOADER_API_DIR` set and verify live Snapshot 3 behavior.
