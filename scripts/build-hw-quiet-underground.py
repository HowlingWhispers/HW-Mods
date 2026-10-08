#!/usr/bin/env python3
"""Reproducibly package the HW Quiet Underground 26.4 Snapshot 3 data pack."""
from __future__ import annotations
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACK = ROOT / "mods" / "hw-quiet-underground" / "datapack"
OUTPUT = ROOT / "dist" / "hw-quiet-underground-1.0.0.zip"

def build() -> pathlib.Path:
    import subprocess
    subprocess.run(["python3", str(ROOT / "tests" / "test-hw-quiet-underground.py")], check=True)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    files = sorted(path for path in PACK.rglob("*") if path.is_file())
    assert any(p.name == "pack.mcmeta" for p in files)
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for file in files:
            arcname = file.relative_to(PACK).as_posix()
            info = zipfile.ZipInfo(arcname, date_time=(2026, 10, 8, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            zf.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    (OUTPUT.parent / (OUTPUT.name + ".sha256")).write_text(f"{digest}  {OUTPUT.name}\n", encoding="utf-8")
    print(f"Built: {OUTPUT}")
    print(f"SHA-256: {digest}")
    return OUTPUT

if __name__ == "__main__":
    build()
