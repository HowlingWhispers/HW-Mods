#!/usr/bin/env python3
"""Safety tests for required quiet-world data pack provisioning."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile

HERE = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("quiet_provision", HERE / "scripts/provision-quiet-underground.py")
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def rejected(fn):
    try:
        fn()
    except (module.UnsafeWorldError, ValueError):
        return
    raise AssertionError("Unsafe world update succeeded")


def main():
    import subprocess
    import sys
    subprocess.run([sys.executable, str(HERE / "scripts/build-hw-quiet-underground.py")],
                   check=True, capture_output=True, text=True)
    pack = HERE / "dist/hw-quiet-underground-1.0.0.zip"
    data = module.verify_pack(pack)
    with tempfile.TemporaryDirectory(prefix="howl-world-bootstrap-") as tmp:
        root = Path(tmp)
        new = root / "brand-new"
        new.mkdir()
        assert module.provision_new_world(new, pack) == "installed"
        target = new / "datapacks/hw-quiet-underground.zip"
        assert target.read_bytes() == data
        assert module.provision_new_world(new, pack) == "already-present"
        assert len(list((new / "datapacks").glob("*.zip"))) == 1

        # Every legacy save is left completely untouched.
        old = root / "older-save"
        old.mkdir()
        (old / "level.dat").write_bytes(b"old world")
        assert module.provision_new_world(old, pack) == "existing-world"
        assert list(old.iterdir()) == [old / "level.dat"]

        generated = root / "generated"
        generated.mkdir()
        (generated / "region").mkdir()
        rejected(lambda: module.provision_new_world(generated, pack))
        assert not (generated / "datapacks").exists()

        modified = root / "conflict"
        modified.mkdir()
        (modified / "datapacks").mkdir()
        (modified / "datapacks/hw-quiet-underground.zip").write_bytes(b"custom content")
        rejected(lambda: module.provision_new_world(modified, pack))
        assert (modified / "datapacks/hw-quiet-underground.zip").read_bytes() == b"custom content"

        community = root / "community"
        community.mkdir()
        (community / "datapacks").mkdir()
        (community / "datapacks/other.zip").write_bytes(b"do not touch")
        assert module.provision_new_world(community, pack) == "installed"
        assert (community / "datapacks/other.zip").read_bytes() == b"do not touch"

        malicious = root / "symlink-world"
        try:
            malicious.symlink_to(old, target_is_directory=True)
        except (NotImplementedError, PermissionError, OSError):
            pass
        else:
            rejected(lambda: module.provision_new_world(malicious, pack))

        bad = root / "bad.zip"
        bad.write_bytes(data)
        (root / "bad.zip.sha256").write_text("0" * 64 + "  bad.zip\n")
        another = root / "another-new-world"
        another.mkdir()
        rejected(lambda: module.provision_new_world(another, bad))
        assert not (another / "datapacks").exists()
    print("PASS: new-world provision, idempotence, legacy saves untouched, conflict, checksum, community packs, symlink")


if __name__ == "__main__":
    main()
