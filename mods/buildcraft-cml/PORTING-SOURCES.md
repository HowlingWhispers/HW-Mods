# Authoritative BCCE porting sources and boundaries

## Immutable upstream baseline

- **Repository:** https://github.com/BCCE-team/BuildCraft
- **Official tag:** [8.0.23](https://github.com/BCCE-team/BuildCraft/releases/tag/8.0.23)
- **Tag commit:** `23c6af379676ce5262c5c0cb6f1f331edc9b12c6`
- **Main reference:** `1.21.11-neoforge` effective sources from BCCE's own `scripts/source_layout.py`
- **Behavioral reference:** BCCE's `1.19.2-forge` gameplay target
- **H.O.W.L. target:** `26.4-snapshot-3` (no Forge/NeoForge runtime)

The previous source cache referenced `b1b166d29da797abf6df3e0618a3bd62f14bc41e`, which is **three commits after the official 8.0.23 tag**. Those commits modified build wrappers/validation, not gameplay, but all new source locks use the release's exact commit to remove ambiguity.

The source is pulled into a **disposable or verified-clean cache** at `dist/buildcraft-upstream/bcce-<commit>`. The prepare script verifies commit identity, clean state, version metadata, module presence and original license. It refuses an unexpected or altered cache rather than guessing.

Run `bash scripts/prepare-buildcraft-bcce.sh` to reconstruct BCCE's complete effective modern source under `dist/buildcraft-reference/1.21.11-neoforge`. The generated `HOWL-SOURCE-MANIFEST.json` reports provenance and module counts. CI checks this reference.

## Preserve these eight upstream modules

| Original module | Primary functional responsibility |
| --- | --- |
| `buildcraft/lib` | Common types, plumbing, shared mechanics and BCCE API integration |
| `buildcraft/core` | Core blocks, tools, services and lifecycle |
| `buildcraft/energy` | Engines, MJ and energy movement |
| `buildcraft/transport` | Real pipes, extraction, routing, fluid/item transport |
| `buildcraft/factory` | Machines, pumps and factory components |
| `buildcraft/silicon` | Gates, assembly and logic |
| `buildcraft/builders` | Quarries, builders, schematics |
| `buildcraft/robotics` | Original robot mechanisms |

All textures, sounds, recipes, models, GUIs, world data, ticks, mechanics and balancing remain parity targets. Original code is preferred wherever game and loader APIs permit. When direct compilation is impossible, adapt compatibility boundaries while preserving behavior. Do not substitute virtual cargo, vanilla glass or command-driven pulses as a release.

## Engine fidelity now landed

`buildcraft/api/v2/energy/MjAmount.java` was copied unmodified from the pinned 8.0.23 BCCE shared API. `BuildCraftRedstoneEngine` adapts BCCE `TileEngineRedstone_BC8` (modern family) and `TileEngineBase_BC8` (NeoForge platform) to a loader-neutral MJ endpoint, preserving 50,000 micro-MJ/t, the 1 MJ buffer, heat thresholds, halved piston speed and stroke-midpoint pulses. Snapshot state is atomically/checksum-saved per real Minecraft world, and loader runtime ticks loaded engine blocks, no surrogate chests.

H.O.W.L.'s native Block and BlockItem bridge still constructs generic blocks; it does not yet provide BuildCraft-compatible `EntityBlock`/`BlockEntityType`, real pipe flow packets, or client rendered item motion. MJ receiver integration is consequently intentionally disabled. Do not ship a fake substitute for these APIs.

## Next native H.O.W.L. prerequisites

1. Snapshot 3 native block/item registration verified in a live client, including the BuildCraft Creative tab and block-item states.
2. Authoritative block entity lifecycle and persistence (create, tick, save, reload, unload, remove).
3. Full transactional inventory, fluid and MJ transport adapters, respecting slot limits, components and rollback.
4. Renderer/model/atlas and machine UI APIs with stable network synchronization.
5. Port BCCE modules in their dependency order. Validate real engine-powered transport first.
6. Stress tests and one-to-one comparisons against original BCCE, then Nightly before Stable.

**No compatibility claim from a compile-only fixture, source materialization or successful asset import.** Test directly in Minecraft; preserve separate test worlds and never rewrite existing saves without a backup/migration plan.

MPL-2.0 notices and per-file attribution are mandatory. Corresponding modified source must remain available under the applicable license terms. The unofficial H.O.W.L. port is not endorsed by the original BuildCraft or BCCE teams.

## BCCE MJ API2 compatibility milestone (0.0.1-dev.1)

The following five unmodified Java sources were brought directly across from
**official BCCE 8.0.23**, retaining the original `buildcraft.api.v2` packages:

- `OperationMode.java`
- `energy/MjPort.java`
- `energy/MjTransferResult.java`
- `energy/MjTransferPolicy.java`
- `energy/MjPortRole.java`

`BuildCraftMjPortAdapter` is deliberately just an interface compatibility
shim from the already adapted `TileEngineRedstone_BC8` to the original
`MjPort` simulate/execute contract and redstone-receiver role. No synthetic
pipe, invented energy mechanic or instant item transfer is used. The original
API2 source is compared byte-for-byte against the pinned upstream in CI.

**Not yet player-ready:** H.O.W.L. still needs the native
`BlockPipeHolder`/`TilePipeHolder` lifecycle, real Minecraft ItemStack
ownership/extraction using BCCE's `ItemPort` contract, and its original
`TravellingItem`/`PipeFlowItems` event and animation systems. Until those
exist, real-world engine ticking deliberately uses `MjEndpoint.NONE` and
cannot emit energy to an imaginary pipe. Continue by exposing the original
pipe BlockEntity and its MJ/item ports, not by implementing a new transport
simulation.

Upstream authoritative source references:

- `source-family-platforms/modern/neoforge/src/main/java/buildcraft/transport/block/BlockPipeHolder.java`
- `source-family-platforms/modern/neoforge/src/main/java/buildcraft/transport/tile/TilePipeHolder.java`
- `source-platforms/neoforge/src/main/java/buildcraft/transport/pipe/behaviour/PipeBehaviourWood.java`
- `source-platforms/neoforge/src/main/java/buildcraft/transport/pipe/flow/PipeFlowItems.java`
- `source-families/modern/src/main/java/buildcraft/transport/pipe/flow/TravellingItem.java`

## Native Minecraft BlockEntityTicker integration (2026-10-09)

H.O.W.L.'s Snapshot 3 native registry now implements Mojang's
`EntityBlock.getTicker(Level, BlockState, BlockEntityType)` for the
shared `buildcrafttransport:pipe_holder` type. It forwards real
server-side tick events from loaded Minecraft block entities into the
registered BuildCraft mod callback. The hook refuses the wrong block
entity type and removed blocks; it never scans fake pipes or runs a
client-side transport simulation. The event currently records only a
diagnostic observation. **It does not run original BCCE pipe gameplay yet.**

### Exact original BCCE lifecycle to port next

Pinned `TilePipeHolder.java`:
`source-family-platforms/modern/neoforge/src/main/java/buildcraft/transport/tile/TilePipeHolder.java`

- `readData(BCValueInput)`, lines 181-217: restores the original pipe from
  `pipe` NBT, its flow/behavior handlers, `plugs`, `wireManager` and the
  six redstone values. The upstream implementation preserves unknown pipe
  NBT rather than deleting it.
- `writeData(BCValueOutput)`, lines 153-179: writes the full original pipe,
  plugs, wires and redstone data. Cargo belongs to the actual pipe's
  original `PipeFlowItems.writeToNbt` / `TravellingItem.writeToNbt`.
- `onLoad()`, lines 303-309: calls `pipe.onLoad()` and validates wires.
- `setRemoved()`, lines 286-290, and `clearRemoved()`, 292-296:
  dispatch invalidation and validation events.
- `update()`, lines 332 onwards: calls `pipe.onTick()`, advances original
  pluggables and batches original render/network updates.
- `BlockPipeHolder.getTicker`, lines 164 onwards, is the upstream bridge
  to `TilePipeHolder.update()`. H.O.W.L.'s new native ticker has the matching
  Minecraft-owned tick boundary, but not the original Java tile implementation.

The **next mandatory development step** is to compile the upstream BCCE
`TilePipeHolder` and its original `Pipe` dependencies against Snapshot 3,
replacing NeoForge-only capabilities/network with the H.O.W.L. boundary while
retaining its original save/load/tick behavior. The current loader-generated
holder is a genuine vanilla block entity *identity*, not an implementation
of BCCE `TilePipeHolder`. Do not enable cargo extraction, public Nightly
or Stable until persistent, real Minecraft ItemStacks run through the original
pipe flows and survive chunk unload/reload.
