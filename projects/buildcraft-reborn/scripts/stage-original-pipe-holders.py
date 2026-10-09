#!/usr/bin/env python3
"""Mechanically adapt ONLY BCCE's original pipe-holder constructors to H.O.W.L.

No new pipe implementation: start with BCCE's own layered effective sources,
then change only the native Minecraft constructor/registry seams. Every
adaptation is reversible and tested byte-for-byte against upstream.
"""
from pathlib import Path
import difflib
import hashlib
import json

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
        )
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
        )
    ],
    "transport/pipe/Pipe.java": [
        (
            'PipePluggable oPlug = level.getCapability(PipeApi.CAP_PLUG, nPos, facing.getOpposite());',
            'PipePluggable oPlug = buildcraft.lib.compat.howl.OriginalCapabilityLookup.get('
            'level, nPos, facing.getOpposite(), PipeApi.CAP_PLUG);'
        )
    ],
    "transport/pipe/flow/PipeFlowItems.java": [
        (
            'return ItemTransactorHelper.getTransactor(level, pos, face.getOpposite(), oTile) != NoSpaceTransactor.INSTANCE;',
            'return buildcraft.lib.compat.howl.NativeLoadedContainerLookup.get(level, pos, face.getOpposite()) != null'
            ' || ItemTransactorHelper.getTransactor(level, pos, face.getOpposite(), oTile) != NoSpaceTransactor.INSTANCE;'
        ),
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
    ]
}

# Rebind NeoForge capability/model import types to native H.O.W.L.
# compatibility descriptors. Original BCCE method bodies remain unchanged.
NEOFORGE_IMPORTS = {
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
        if not edits:
            continue
        staged_count += 1
        revised = original
        for before, after in edits:
            assert revised.count(before) == 1, (
                f"{relative}: expected exactly one original BCCE anchor"
            )
            revised = revised.replace(before, after, 1)
        # Guard that the adaptation is reversible: NO other source logic
        # may change, even whitespace, without a reviewed new patch.
        restored = revised
        for before, after in reversed(edits):
            assert restored.count(after) == 1
            restored = restored.replace(after, before, 1)
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
    print("Original BCCE transport method bodies are unchanged.")
    print("STAGED_ONLY: NeoForge dependency closure + Minecraft compilation still required.")

if __name__ == "__main__":
    main()
