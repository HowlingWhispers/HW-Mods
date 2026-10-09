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
