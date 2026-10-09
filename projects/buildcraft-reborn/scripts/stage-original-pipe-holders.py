#!/usr/bin/env python3
"""Mechanically adapt BCCE's original pipe-holder dependency closure to H.O.W.L.

No new pipe implementation: start with BCCE's own layered effective sources,
then change the native Minecraft and platform API seams. Every
adaptation is reversible and tested byte-for-byte against upstream.
"""
from pathlib import Path
import difflib
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[3]
PROJECT = ROOT / "projects/buildcraft-reborn"
BASE = ROOT / "dist/buildcraft-reborn"
SOURCE = BASE / "effective-1.21.11-neoforge/src/main/java/buildcraft"
DEST = BASE / "staged-snapshot3/src/main/java/buildcraft"
BLOCK = "transport/block/BlockPipeHolder.java"
TILE = "transport/tile/TilePipeHolder.java"
REG = ('net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE'
       '.getValue(Identifier.parse("hw_buildcraft_reborn:pipe_holder"))')
CHANGES = {
    BLOCK: [
        (
            'import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;',
            '// H.O.W.L. owns client model/render registration, not NeoForge block extensions.'
        ),
        (
            '    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {\n'
            '        consumer.accept(BlockPipeHolderClientExtensions.INSTANCE);\n'
            '    }',
            '    // H.O.W.L. renders this original pipe through its native client registration.'
        ),
        (
            '\tpublic BlockPipeHolder() {\n'
            '\t\tsuper(RegistryCompat.blockProperties(BlockBehaviour.Properties.of())'
            '.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(0.25f)\n'
            '\t\t\t\t.explosionResistance(3.0f).noOcclusion());',
            '\tpublic BlockPipeHolder(BlockBehaviour.Properties nativeProperties) {\n'
            '\t\tsuper(nativeProperties.mapColor(MapColor.STONE).sound(SoundType.STONE)'
            '.strength(0.25f)\n'
            '\t\t\t\t.explosionResistance(3.0f).noOcclusion());'
        ),
        (
            'BCTransportBlocks.PIPE_HOLDER_BE.get()',
            REG
        ),
        (
            'import net.minecraft.world.level.block.SoundType;',
            '// Snapshot 3 block sounds are keyed by the original stone sound-set id.'
        ),
        (
            '.sound(SoundType.STONE)',
            '.sound(net.minecraft.resources.ResourceKey.create('
            'net.minecraft.core.registries.Registries.BLOCK_SOUND_SET, '
            'Identifier.parse("minecraft:stone")))'
        ),
        (
            'this.spawnDestroyParticles(world, player, pos, state);',
            'this.spawnDestroyParticles(world, pos, state);'
        ),
        (
            'return super.getExplosionResistance(state, level, pos, explosion);',
            'return super.getExplosionResistance();'
        ),
        (
            'public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be,',
            'public void playerDestroy(ServerLevel world, net.minecraft.server.level.ServerPlayer player, BlockPos pos, BlockState state, BlockEntity be,'
        ),
        (
            '((TilePipeHolder) BlockEntity).update();',
            '((TilePipeHolder) BlockEntity).ensureNativeLoaded();\n'
            '\t\t\t\t((TilePipeHolder) BlockEntity).update();'
        ),
    ],
    TILE: [
        (
            '    public TilePipeHolder(BlockPos pos, BlockState bs) {\n'
            '    \tsuper(BCTransportBlocks.PIPE_HOLDER_BE.get(), pos, bs);',
            '    public TilePipeHolder(BlockPos pos, BlockState bs) {\n'
            '    \tsuper(' + REG + ', pos, bs);'
        ),
        (
            'IPipe neighbourPipe = level.getCapability(PipeApi.CAP_PIPE, neighbourPos, side.getOpposite());',
            'IPipe neighbourPipe = buildcraft.lib.compat.howl.OriginalCapabilityLookup.get('
            'level, neighbourPos, side.getOpposite(), PipeApi.CAP_PIPE);'
        ),
        (
            'return level.getCapability(capability, neighbourPos, targetSide);',
            'return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get('
            'level, neighbourPos, targetSide, capability);'
        ),
        (
            'level.invalidateCapabilities(worldPosition);',
            '// H.O.W.L. capability reads are uncached; the original neighbour notifications below refresh topology.',
            2
        ),
        (
            'BCTransportBlocks.pipeHolder.get()',
            'getBlockState().getBlock() /* native holder */',
            2
        ),
        (
            'import buildcraft.transport.client.model.ModelPipe;',
            '// Model snapshots are produced by the original TilePipeHolderModelData helper.'
        ),
        (
            'import buildcraft.transport.BCTransportBlocks;',
            '// Holder registration comes from the native H.O.W.L. type and current block state.'
        ),
    ],
    "transport/pipe/Pipe.java": [
        (
            'PipePluggable oPlug = level.getCapability(PipeApi.CAP_PLUG, nPos, facing.getOpposite());',
            'PipePluggable oPlug = buildcraft.lib.compat.howl.OriginalCapabilityLookup.get('
            'level, nPos, facing.getOpposite(), PipeApi.CAP_PLUG);'
        ),
        (
            'holder.getPipeWorld().random.nextLong()',
            'holder.getPipeWorld().getRandom().nextLong()'
        )
    ],
    "transport/pipe/flow/PipeFlowItems.java": [
        (
            'ItemStack possible = trans.extract(filter, 1, count, true);',
            'buildcraft.api.v2.item.ItemPort nativeSource = trans == NoSpaceTransactor.INSTANCE'
            ' ? buildcraft.lib.compat.howl.NativeLoadedContainerLookup.get(level, targetPos, from.getOpposite()) : null;\n'
            '        ItemStack possible = nativeSource == null ? trans.extract(filter, 1, count, true)'
            ' : nativeSource.extract(filter::matches, 1, count, buildcraft.api.v2.OperationMode.SIMULATE).transferred();'
        ),
        (
            'ItemStack stack = trans.extract(filter, count, count, simulate == FluidAction.SIMULATE);',
            'ItemStack stack = nativeSource == null ? trans.extract(filter, count, count, simulate == FluidAction.SIMULATE)'
            ' : nativeSource.extract(filter::matches, count, count, simulate == FluidAction.SIMULATE'
            ' ? buildcraft.api.v2.OperationMode.SIMULATE : buildcraft.api.v2.OperationMode.EXECUTE).transferred();'
        ),

        (
            'return ItemTransactorHelper.getTransactor(oTile, face.getOpposite()) != NoSpaceTransactor.INSTANCE;',
            'return (oTile != null && oTile.getLevel() != null && '
            'buildcraft.lib.compat.howl.NativeLoadedContainerLookup.get('
            'oTile.getLevel(), oTile.getBlockPos(), face.getOpposite()) != null)'
            ' || ItemTransactorHelper.getTransactor(oTile, face.getOpposite()) != NoSpaceTransactor.INSTANCE;'
        ),
        (
            'return ItemTransactorHelper.getTransactor(level, pos, face.getOpposite(), oTile) != NoSpaceTransactor.INSTANCE;',
            'return buildcraft.lib.compat.howl.NativeLoadedContainerLookup.get(level, pos, face.getOpposite()) != null'
            ' || ItemTransactorHelper.getTransactor(level, pos, face.getOpposite(), oTile) != NoSpaceTransactor.INSTANCE;'
        ),
        (
            'excess = transactor.insert(excess, false, false);',
            'if (transactor == NoSpaceTransactor.INSTANCE) {\n'
            '                            buildcraft.api.v2.item.ItemPort port = '
            'buildcraft.lib.compat.howl.NativeLoadedContainerLookup.get(level, targetPos, oppositeSide);\n'
            '                            if (port != null) {\n'
            '                                var moved = port.insert(excess, buildcraft.api.v2.OperationMode.EXECUTE);\n'
            '                                excess = moved.remainderCount() == 0 ? ItemStack.EMPTY'
            ' : excess.copyWithCount(moved.remainderCount());\n'
            '                            }\n'
            '                        } else {\n'
            '                            excess = transactor.insert(excess, false, false);\n'
            '                        }'
        ),
    ],
    "transport/internal/pipe/PipeDefinition.java": [
        (
            'import net.neoforged.fml.ModContainer;\nimport net.neoforged.fml.ModLoadingContext;',
            'import buildcraft.lib.compat.howl.ActiveModNamespace;'
        ),
        (
            'ModContainer mod = ModLoadingContext.get().getActiveContainer();\n'
            '            if (mod == null) {\n'
            '                throw new IllegalStateException(\n'
            '                    "Cannot interact with PipeDefinition outside of an actively scoped mod!");\n'
            '            }\n'
            '            return mod.getModId();',
            'return ActiveModNamespace.get();'
        )
    ],
    "lib/block/BlockBCBase_Neptune.java": [
        (
            'import net.minecraft.world.level.block.SoundType;',
            '// Snapshot 3 block sounds use registry-backed sound-set keys.'
        ),
        (
            '.sound(SoundType.METAL)',
            '.sound(net.minecraft.resources.ResourceKey.create('
            'net.minecraft.core.registries.Registries.BLOCK_SOUND_SET, '
            'net.minecraft.resources.Identifier.parse("minecraft:metal")))'
        )
    ],
    "lib/block/BlockBCTile_Neptune.java": [
        (
            'return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);',
            'return level.isClientSide() ? level.setBlock(pos, fluid.createLegacyBlock(), 11) : level.removeBlock(pos, false);'
        ),
        (
            'state.getBlock().getCloneItemStack(\n'
            '                blockEntity.getLevel(), blockEntity.getBlockPos(), state, false, null\n'
            '            )',
            'state.getCloneItemStack(blockEntity.getLevel(), blockEntity.getBlockPos(), false)'
        ),
        (
            'super.onNeighborChange(state, level, pos, neighbor);',
            '// The inherited NeoForge default callback has no body; the original tile hook above owns invalidation.'
        ),
        (
            'public void wasExploded(Level world, BlockPos pos, Explosion explosion)',
            'public void wasExploded(net.minecraft.server.level.ServerLevel world, BlockPos pos, Explosion explosion)'
        ),
        (
            'tile.update();',
            'tile.ensureNativeLoaded();\n\t\t\t\ttile.update();'
        ),
    ],
    "lib/compat/minecraft/persistence/BCBlockEntity.java": [
        (
            '    protected void readCommonData(BCValueInput input) {}',
            '    private boolean nativeLoaded;\n\n'
            '    /** Run the original load hook once, immediately before the first native world tick. */\n'
            '    public final void ensureNativeLoaded() {\n'
            '        if (!nativeLoaded && level != null && !isRemoved()) {\n'
            '            nativeLoaded = true;\n'
            '            onLoad();\n'
            '        }\n'
            '    }\n\n'
            '    public void onLoad() { requestModelDataUpdate(); }\n'
            '    // Original NeoForge base implementation is empty. Chunk-unload dispatch still needs a native hook.\n'
            '    public void onChunkUnloaded() {}\n\n'
            '    public void requestModelDataUpdate() {\n'
            '        if (level != null && level.isClientSide()) {\n'
            '            BlockState state = getBlockState();\n'
            '            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);\n'
            '        }\n'
            '    }\n\n'
            '    @Override\n'
            '    public void setRemoved() {\n'
            '        nativeLoaded = false;\n'
            '        super.setRemoved();\n'
            '    }\n\n'
            '    protected void readCommonData(BCValueInput input) {}'
        ),
    ],
    "lib/misc/ChunkUtil.java": [
        ('pos.x, pos.z', 'pos.x(), pos.z()'),
        ('chunk.getPos().x == x && chunk.getPos().z == z',
         'chunk.getPos().x() == x && chunk.getPos().z() == z'),
    ],
}

# Rebind NeoForge capability/model import types to native H.O.W.L.
# compatibility descriptors. Original BCCE method bodies remain unchanged.
NEOFORGE_IMPORTS = {
    "import net.neoforged.neoforge.items.IItemHandler;":
        "import buildcraft.lib.compat.howl.storage.IItemHandler;",
    "import net.neoforged.neoforge.items.IItemHandlerModifiable;":
        "import buildcraft.lib.compat.howl.storage.IItemHandlerModifiable;",
    "import net.neoforged.neoforge.capabilities.BlockCapability;":
        "import buildcraft.lib.compat.howl.BlockCapability;",
    "import net.neoforged.neoforge.model.data.ModelData;":
        "import buildcraft.lib.compat.howl.ModelData;",
    "import net.neoforged.neoforge.model.data.ModelProperty;":
        "import buildcraft.lib.compat.howl.ModelProperty;",
    "import net.neoforged.neoforge.client.model.data.ModelData;":
        "import buildcraft.lib.compat.howl.ModelData;",
    "import net.neoforged.neoforge.client.model.data.ModelProperty;":
        "import buildcraft.lib.compat.howl.ModelProperty;",
}

def main():
    assert not (ROOT / "mods/buildcraft-cml").exists(), "Retired source must stay deleted"
    assert SOURCE.is_dir(), "Materialize the pinned original source before staging"
    # A removed adaptation must never survive in the compiler's preferred sourcepath.
    if DEST.exists():
        shutil.rmtree(DEST)
    report = {
        "status": "STAGED_NOT_COMPILED",
        "origin": "BCCE-team/BuildCraft@23c6af379676ce5262c5c0cb6f1f331edc9b12c6",
        "minecraft": "26.4-snapshot-3",
        "loader_block_entity_id": "hw_buildcraft_reborn:pipe_holder",
        "files": []
    }
    staged_count = 0
    for p in sorted(SOURCE.rglob("*.java")):
        relative = p.relative_to(SOURCE).as_posix()
        original = p.read_text(encoding="utf-8")
        edits = CHANGES.get(relative, [])[:]
        for before, after in NEOFORGE_IMPORTS.items():
            if before in original:
                assert original.count(before) == 1
                edits.append((before, after))
        # Retain the original simulation enum across item and fluid method signatures.
        # This is a type migration, never a replacement extraction or fluid algorithm.
        action_type = "net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction"
        if action_type in original:
            edits.append((action_type, "buildcraft.lib.compat.howl.FluidAction", original.count(action_type)))
        if not edits:
            continue
        staged_count += 1
        revised = original
        for edit in edits:
            before, after = edit[:2]
            count = edit[2] if len(edit) == 3 else 1
            assert revised.count(before) == count, (
                f"{relative}: expected {count} original BCCE anchors, found {revised.count(before)}"
            )
            revised = revised.replace(before, after, count)
        # Guard that the adaptation is reversible: NO other source logic
        # may change, even whitespace, without a reviewed new patch.
        restored = revised
        for edit in reversed(edits):
            before, after = edit[:2]
            count = edit[2] if len(edit) == 3 else 1
            assert restored.count(after) == count, (relative, after)
            restored = restored.replace(after, before, count)
        assert restored == original, "Adaptation changed original gameplay code"
        # Preserve important original BCCE methods and flow semantics.
        if relative == BLOCK:
            assert "return new TilePipeHolder(pos, state);" in revised
            assert "((TilePipeHolder) BlockEntity).update();" in revised
            assert "getCollisionShape(" in revised
            assert "getShape(" in revised
        elif relative == TILE:
            assert "OriginalCapabilityLookup.get(" in revised
            for name in ("void update()", "void writeData(", "void readData(",
                         "void onChunkUnloaded()", "void onLoad()"):
                assert name in revised, f"Original BCCE lifecycle missing: {name}"
            assert "pipe.onTick();" in revised
        elif relative == "transport/pipe/flow/PipeFlowItems.java":
            # Preserve the original event-driven travelling-item flow.
            # The native inventory path is only an endpoint fallback.
            for name in ("void onTick()", "onItemReachCenter(",
                         "onItemReachEnd(", "insertItemEvents(",
                         "scheduleTravellingItem(", "holder.fireEvent("):
                assert name in revised, f"Original BCCE item flow missing: {name}"
            assert "NativeLoadedContainerLookup.get(" in revised
            assert "OperationMode.SIMULATE" in revised
            assert "OperationMode.EXECUTE" in revised
            assert "NoSpaceTransactor.INSTANCE" in revised
        elif relative == "lib/block/BlockBCBase_Neptune.java":
            assert "public BlockBCBase_Neptune(BlockBehaviour.Properties prop)" in revised
            assert "super(RegistryCompat.blockProperties(prop));" in revised
        dest = DEST / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(revised, encoding="utf-8", newline="")
        diff = list(difflib.unified_diff(original.splitlines(),
                                        revised.splitlines(),
                                        fromfile="BCCE/" + relative,
                                        tofile="HOWL-source-port/" + relative,
                                        lineterm=""))
        report["files"].append({
            "source": relative,
            "original_sha256": hashlib.sha256(original.encode()).hexdigest(),
            "adapted_sha256": hashlib.sha256(revised.encode()).hexdigest(),
            "modified_anchors": len(edits),
            "diff_lines": len(diff)
        })
        print("\n".join(diff))
    manifest = BASE / "staged-snapshot3/ADAPTATIONS.json"
    manifest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    assert staged_count == len(report["files"])
    print(f"PASS: {staged_count} original BCCE source classes staged via reversible "
          "capability/model imports and constructor/sound compatibility seams.")
    print("Original travelling-item routing and pipe algorithms are retained; API seams are listed in ADAPTATIONS.json.")
    print("STAGED_ONLY: NeoForge dependency closure + Minecraft compilation still required.")

if __name__ == "__main__":
    main()
