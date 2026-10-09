#!/usr/bin/env python3
"""Verify true BuildCraft 8.0.23 original-source provenance and port isolation.

A pass means SOURCE IMPORT ONLY. It is never evidence of gameplay parity.
"""
import json
from pathlib import Path
import sys

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parents[1]
LOCK = json.loads((PROJECT / "UPSTREAM.lock.json").read_text(encoding="utf-8"))
ORIGINAL_SHA = "23c6af379676ce5262c5c0cb6f1f331edc9b12c6"
COMPONENTS = (
    "lib", "core", "energy", "transport",
    "factory", "builders", "silicon", "robotics",
)
CRITICAL = (
    "transport/block/BlockPipeHolder.java",
    "transport/tile/TilePipeHolder.java",
    "transport/pipe/flow/PipeFlowItems.java",
    "transport/pipe/flow/TravellingItem.java",
    "transport/pipe/behaviour/PipeBehaviourWood.java",
    "transport/BCTransportPipes.java",
    "transport/BCTransportRegistries.java",
    "core/blockEntity/TileEngineRedstone_BC8.java",
    "lib/engine/TileEngineBase_BC8.java",
    "core/client/render/RenderEngine_BC8.java",
    "transport/client/render/RenderPipeHolder.java",
)
FORBIDDEN_JAVA = {
    "PipeNetwork.java",
    "PipeNetworkStore.java",
    "BuildCraftGlassPipeDemo.java",
    "BuildCraftRedstoneEngine.java",
}


def verify(upstream: Path, effective: Path) -> None:
    assert not (ROOT / "mods" / "buildcraft-cml").exists(), (
        "Old BuildCraft experiment must not return"
    )
    assert LOCK["project_id"] == "hw_buildcraft_reborn"
    assert LOCK["project_version"] == "0.0.1-dev.1"
    assert LOCK["status"] == "SOURCE_IMPORT_ONLY_NOT_PLAYABLE"
    assert LOCK["upstream"]["commit"] == ORIGINAL_SHA
    assert LOCK["upstream"]["release"] == "8.0.23"
    assert LOCK["upstream"]["effective_source_target"] == "1.21.11-neoforge"
    assert LOCK["port_target"]["minecraft"] == "26.4-snapshot-3"
    assert LOCK["port_target"]["namespace"] == "hw_buildcraft_reborn"
    assert LOCK["port_target"]["jar_prefix"] == "hw-buildcraft-reborn"
    assert len(LOCK["original_files"]) >= 12
    assert len(LOCK["original_files"]) == len(set(LOCK["original_files"]))

    # No edits to original sources, even whitespace and comments, in the
    # baseline. Future changes must be explicitly isolated as adapters.
    for relative in LOCK["original_files"]:
        rel = Path(relative)
        assert not rel.is_absolute() and ".." not in rel.parts
        vendored = PROJECT / "vendor" / "bcce-8.0.23" / rel
        origin = upstream / rel
        assert vendored.is_file() and origin.is_file(), relative
        assert vendored.read_bytes() == origin.read_bytes(), (
            "Original BuildCraft code was modified: " + relative
        )

    assert b"Mozilla Public License Version 2.0" in (
        PROJECT / "vendor/bcce-8.0.23/LICENSE.txt"
    ).read_bytes()
    java = effective / "src/main/java/buildcraft"
    total = 0
    for module in COMPONENTS:
        entries = list((java / module).rglob("*.java"))
        assert entries, "Missing original BCCE gameplay module: " + module
        total += len(entries)

    for path in CRITICAL:
        file = java / path
        assert file.is_file() and file.stat().st_size > 100, (
            "Original BCCE source absent from its own effective layout: " + path
        )

    # Only reviewed boundary types are permitted outside the ORIGINAL BCCE.
    # None implement pipe flow, item motion, animation or MJ gameplay.
    active_java = {x.relative_to(PROJECT).as_posix() for x in PROJECT.rglob("*.java")
                   if "vendor" not in x.parts and "tests" not in x.parts}
    allowed_java = {
        "bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/ActiveModNamespace.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/BlockCapability.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/NativeCapabilityAccess.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/OriginalCapabilityLookup.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/NativeContainerItemPort.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/NativeLoadedContainerLookup.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/ModelData.java",
        "bridge/src/main/java/buildcraft/lib/compat/howl/ModelProperty.java",
    }
    assert active_java == allowed_java, (
        "Unexpected game code outside original BCCE: " + str(active_java ^ allowed_java)
    )
    bridge = PROJECT / "bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java"
    binding = bridge.read_text(encoding="utf-8")
    for expected in (
        "import buildcraft.transport.block.BlockPipeHolder;",
        "import buildcraft.transport.tile.TilePipeHolder;",
        "new BlockPipeHolder(",
        "new TilePipeHolder(",
        'HOLDER_ID = "hw_buildcraft_reborn:pipe_holder"',
        "registerNativeKeyedBlockFactory(",
        "registerNativeBlockEntityFactory(",
        "context.registerBlockEntityType(HOLDER_ID, List.of(HOLDER_ID));",
    ):
        assert expected in binding, "Original BCCE registration bridge missing: " + expected
    for forbidden in (
        "registerServerTick(", "registerBlockEntityTick(", "PipeNetwork",
        "BuildCraftGlassPipeDemo", "mods/buildcraft-cml", "buildcraft_cml"
    ):
        assert forbidden not in binding, (
            "Bridge must not reintroduce the old custom BuildCraft: " + forbidden
        )
    assert (PROJECT / "tests/OriginalPipeSchedulingTest.java").is_file()
    assert not any(x.name in FORBIDDEN_JAVA for x in PROJECT.rglob("*.java"))
    assert not (PROJECT / "resources/coda.mod.json").exists(), (
        "No runnable H.O.W.L. mod exists yet; do not advertise a fake build"
    )
    print(
        f"PASS: {len(LOCK['original_files'])} byte-identical original files; "
        f"{total} original gameplay Java files across {len(COMPONENTS)} modules."
    )
    print("PASS: unique new project identity and retired prototype isolation.")
    print("STATUS: SOURCE IMPORT ONLY; no Minecraft/loader compatibility claimed.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify_original.py <upstream checkout> <effective BCCE tree>")
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
