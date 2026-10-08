#!/usr/bin/env python3
"""Internal world-creation bootstrap for mandatory HW Quiet Underground.

Call this only after Minecraft creates a *new* world folder, but BEFORE it scans
its initial data packs or generates any chunks. It is not an existing-world
migration tool and is not exposed as an optional player install command.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PACK = ROOT / "dist" / "hw-quiet-underground-1.0.0.zip"
NAME = "hw-quiet-underground.zip"
REQUIRED = {
    "pack.mcmeta",
    "data/minecraft/worldgen/carver/canyon.json",
    "data/minecraft/worldgen/carver/cave.json",
    "data/minecraft/worldgen/carver/cave_extra_underground.json",
    "data/minecraft/worldgen/density_function/overworld/final_density.json",
}
MAX_BYTES = 8 * 1024 * 1024


class UnsafeWorldError(RuntimeError):
    """World is not a safe, ungenerated world-creation target."""


def _checksum(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def verify_pack(pack: Path) -> bytes:
    if pack.is_symlink() or not pack.is_file() or pack.stat().st_size > MAX_BYTES:
        raise ValueError("Quiet Underground bundle is absent, unsafe, or too large")
    data = pack.read_bytes()
    with zipfile.ZipFile(pack) as archive:
        if not REQUIRED.issubset(archive.namelist()):
            raise ValueError("Quiet Underground bundle is incomplete")
        if any(i.file_size > MAX_BYTES for i in archive.infolist()):
            raise ValueError("Quiet Underground bundle has oversized entries")
        import json
        meta = json.loads(archive.read("pack.mcmeta"))
        if meta["pack"]["min_format"] != 123 or meta["pack"]["max_format"] != 123:
            raise ValueError("Quiet Underground targets the wrong Minecraft data format")
    sha_file = pack.with_name(pack.name + ".sha256")
    if not sha_file.is_file() or sha_file.is_symlink():
        raise ValueError("Quiet Underground bundle checksum file is missing")
    expected = sha_file.read_text(encoding="ascii").split()[0].lower()
    if len(expected) != 64 or expected != _checksum(data):
        raise ValueError("Quiet Underground bundle failed SHA-256 verification")
    return data


def provision_new_world(world: Path, pack: Path = DEFAULT_PACK) -> str:
    """Return installed/already-present/existing-world; never modify old worlds."""
    world = Path(world)
    if world.is_symlink() or not world.is_dir():
        raise UnsafeWorldError("World folder must already exist and must not be a symlink")
    if (world / "level.dat").exists() or (world / "level.dat_old").exists():
        return "existing-world"
    if any((world / name).exists() for name in ("region", "entities", "poi", "DIM-1", "DIM1")):
        raise UnsafeWorldError("Refusing world with generated chunk or dimension data")
    if any(p.name not in {"datapacks", "session.lock"} for p in world.iterdir()):
        raise UnsafeWorldError("World folder contains unexpected data")
    data = verify_pack(pack)
    folder = world / "datapacks"
    if folder.is_symlink():
        raise UnsafeWorldError("World data pack directory is a symlink")
    folder.mkdir(exist_ok=True)
    target = folder / NAME
    if target.is_symlink():
        raise UnsafeWorldError("Quiet Underground target is a symlink")
    if target.exists():
        if target.read_bytes() == data:
            return "already-present"
        raise UnsafeWorldError("Existing world data pack differs; will not overwrite")
    temp: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(prefix=".hw-quiet-", suffix=".tmp",
                                         dir=folder, delete=False) as stream:
            temp = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        # No overwrites, including simultaneous world creation attempts.
        os.link(temp, target)
        return "installed"
    finally:
        if temp is not None:
            temp.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Developer world-creation bootstrap (not an optional player mod)")
    parser.add_argument("--world-dir", type=Path, required=True)
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK)
    args = parser.parse_args()
    print(provision_new_world(args.world_dir, args.pack))


if __name__ == "__main__":
    main()
