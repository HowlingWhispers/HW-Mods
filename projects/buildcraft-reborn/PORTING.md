# BuildCraft Reborn: compatibility work order

The foundation is **original BuildCraft**, not HW-Mods' deleted custom
simulation. Each stage must point back to the actual BCCE class it adapts.

| Priority | Original BCCE components | Target acceptance |
| --- | --- | --- |
| 1 | `BlockPipeHolder`, `TilePipeHolder`, `BCTransportPipes` | Original pipe holder constructs as a H.O.W.L.-registered Minecraft block entity in a loaded world |
| 2 | `PipeFlowItems`, `TravellingItem`, `PipeBehaviourWood` | Original item extraction and travelling-stack state, not an instant chest-to-chest transfer |
| 3 | `TileEngineRedstone_BC8`, `TileEngineBase_BC8` | Original engine supplies its existing MJ mechanics through native ports |
| 4 | `RenderPipeHolder`, `RenderEngine_BC8` | Original animations and item packets are visible in-game |
| 5 | Original NBT and chunk lifecycle | Save, unload/reload and no item dupes or losses |
| 6 | Original factory, builders, silicon and robotics modules | Additional original mechanics adapted in order, no invented substitutes |

**Build gate:** no `coda.mod.json` or release job until the actual
original BuildCraft classes compile against the targeted game and loader.
CI currently validates **source provenance only**, not gameplay.

**Do not import** `mods/buildcraft-cml/`, `PipeNetwork.java`,
`BuildCraftGlassPipeDemo.java`, the static engine model, or old
BuildCraft Nightly asset names. Do not load an old BuildCraft test
save into the new mod. H.O.W.L. may gain generic lifecycle APIs, but
no fabricated pipe/engine gameplay is allowed.

## Source-backed scheduling baseline

The unmodified upstream `buildcraft.lib.misc.data.DelayedList` is the
queue directly used by BCCE `PipeFlowItems` when advancing travelling
items. `buildcraft.api.v2.pipe.ItemTransportProfile` is its original
configured item routing policy. Both are compiled and exercised in CI in
their original packages, never replaced with a homemade network. This is
the first executable upstream regression, not a runnable mod.

No artificial Minecraft block/entity implementations, transport shortcuts
or legacy `PipeNetwork` classes may be added to this project. The next
compile milestone is original `TilePipeHolder` and `BlockPipeHolder`
against Snapshot 3 and H.O.W.L. with explicit, reviewed compatibility
adaptations.

## Original Minecraft ItemStack compatibility

The source-first boundary retains the original `ItemPort` and
`ItemTransferResult` Java implementations and tests them with the
actual verified Mojang Snapshot 3 `ItemStack` class. This is
a strict compilation test, not an invented item-transfer system.
The next step must connect those unchanged classes to the original
`TilePipeHolder`, `PipeFlowItems`, and real Minecraft containers
through H.O.W.L.'s new native factory API, while retaining BCCE
save/load and renderer behavior.

## Stage 1 original pipe constructor adaptation (in progress)

Original BCCE `BlockPipeHolder` and `TilePipeHolder` are staged
into `dist/buildcraft-reborn/staged-snapshot3/` with only these known
constructor/registry seams patched, each reversible byte-for-byte:

- `BlockPipeHolder()` -> `BlockPipeHolder(nativeProperties)`
  while retaining original map color, sound, strength, waterlogging,
  bounding boxes and ticking methods
- NeoForge `BCTransportBlocks.PIPE_HOLDER_BE.get()` comparison in
  `BlockPipeHolder.getTicker` -> native registry lookup by the new
  `hw_buildcraft_reborn:pipe_holder` ID
- NeoForge `BCTransportBlocks.PIPE_HOLDER_BE.get()` base constructor
  argument in `TilePipeHolder` -> the same native registry lookup

Original `getTicker` still calls original `TilePipeHolder.update`,
and the tile still calls original `pipe.onTick()`. This does not
reimplement either one. Original persistence and pluggable methods
remain unchanged.

The staged classes still require compilation of upstream BCCE library
bases plus removal/adaptation of NeoForge-only references before they
can run. The explicit compiler probe gives **NOT_COMPILED** status
and saves all javac errors rather than silently treating import or
factory tests as proof of original pipe gameplay. Neither a playable
JAR nor any package for CodaLauncher is created.

## Explicit original class registration

See `bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java`.
It is the *actual* integration source for the original BCCE
`BlockPipeHolder` and `TilePipeHolder` constructors, not a
stand-in `Block` or empty `BlockEntity`. It registers only BCCE's
shared pipe holder, not invented wood/cobblestone blocks.
Eventually BCCE's original `ItemPipeHolder` will be responsible for
installing pipe variants, exactly as upstream does.

The registration bridge cannot compile until the original upstream
dependencies are ported to Minecraft 26.4 Snapshot 3. In particular
NeoForge `BlockCapability`, `ModelData`, item/fluids capabilities
and Minecraft's removed `SoundType` must be handled as explicit
API compatibility work. CI retains the exact compiler report; no
synthetic inventory/network or new public BuildCraft Nightly may
substitute for this.

## Sound API migration

The original BCCE `BlockPipeHolder` constructor uses
`SoundType.STONE`, and its `BlockBCBase_Neptune` superclass has
a `SoundType.METAL` default constructor. Minecraft 26.4 has
removed `SoundType`. Stage their original sound choices as
`ResourceKey<BlockSoundSet>` for `minecraft:stone` and
`minecraft:metal`, using native `Registries.BLOCK_SOUND_SET`.
Never remove the original pipe sound setting to mask compile errors.

These are reversible source-level changes. All upstream methods still
belong to BCCE; no custom audio or fake pipe system was introduced.

## NeoForge capability and model-data boundary (in progress)

New **H.O.W.L.-only type adapters**, not new BuildCraft mechanics:

- `buildcraft.lib.compat.howl.BlockCapability` preserves canonical
  BCCE capability identity, sided type and strict return-type validation.
- `NativeCapabilityAccess.get` reads an **existing, loaded, real Minecraft
  BlockEntity** and delegates to the caller's actual original BCCE capability
  provider. It does not create an inventory or load missing chunks. This
  lookup still needs wiring to BCCE's original `IBCCapabilityProvider` in
  the full original class port.
- `ModelProperty` and immutable `ModelData` preserve the **original
  BCCE baked `PipeRenderData` snapshot**, not a replacement pipe renderer.
  Native H.O.W.L. chunk meshing/render invalidation is still required.

`scripts/stage-original-pipe-holders.py` now stages all original BCCE
classes that reference NeoForge's `BlockCapability`, `ModelData` or
`ModelProperty`, replacing **only the exact import lines**. Each edit
must be reversible without modifying the original BCCE method body.
The original BCCE source remains untouched.

Compilation of the type adapters against the **real Mojang Snapshot 3**
classes is covered by `NativeCapabilityModelDataTest.java`. This does
not satisfy the playable BuildCraft milestone: vanilla chest exposure,
sided pluggable/pipe queries, actual chunk rendering, original tile
persistence and the other NeoForge ABI dependencies must be completed.

## BCCE original pipe-to-pipe capability discovery

`OriginalCapabilityLookup.get` now connects to the **original**
`IBCCapabilityProvider.getCapability` on a real loaded Minecraft
`BlockEntity`. It never fabricates a block entity, loads missing
chunks, or bypasses the original BCCE pluggable blocking logic.

Reversible source substitutions are staged only at three specific
NeoForge positional lookup sites:
- `TilePipeHolder.getNeighbourPipe`, querying the original
  `PipeApi.CAP_PIPE`
- `TilePipeHolder.getCapabilityFromPipe`, querying the original
  `getCapability` path
- `Pipe`'s CAP_PLUG lookup when building original pipe topology

This initial bridge covers **BCCE native block-entity providers only**.
Discovering vanilla chest/barrel automation on 26.4 still needs a
real inventory adapter to the original BCCE `IItemHandler` or
`ItemPort`, including proper simulate/execute behavior, before item
transport may be released. Missing capabilities are not substituted
with fake inventory data or teleportation.

## Native vanilla inventory endpoint for original BCCE ItemPort

`NativeContainerItemPort` is a **Minecraft Container adapter** that
implements the unchanged BCCE API2 `ItemPort` interface. It mutates
real Minecraft `Container` slots with their original `ItemStack`
components, without introducing pipe routing or teleportation.

- Distinguishes `SIMULATE` from `EXECUTE`
- Honors `ALL_OR_NOTHING` versus `PARTIAL` for insertion
- Honors original ranged extraction minimums
- Uses vanilla inventory stack-size and `canPlaceItem` constraints
- Supports vanilla `WorldlyContainer` directional insertion/extraction
- Never coalesces item stacks with different components
- Never fills a synthetic intermediate inventory

The exact Mojang `SimpleContainer` and `Items` are used by
`NativeContainerItemPortTest.java` in GitHub Actions, with the Mojang
`Bootstrap` initialized and BCCE's **original** transfer-result
types compiled. This proves actual container-level operations, not
complete BuildCraft pipe mechanics. The original `PipeFlowItems`
and NeoForge/BCCE `IItemHandler` source bridge still need hooking up.
No public JAR until the original BCCE pipe gameplay compiles.
