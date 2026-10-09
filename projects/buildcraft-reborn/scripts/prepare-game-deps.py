#!/usr/bin/env python3
"""Fetch and verify the actual Snapshot 3 compiler/runtime dependencies."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "dist/buildcraft-reborn"
CACHE = BASE / "mojang-26.4-snapshot-3"


def fetch(url, path, sha1=None):
    if path.is_file() and (sha1 is None or hashlib.sha1(path.read_bytes()).hexdigest() == sha1):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".download")
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
                    "--retry", "3", "--max-time", "180", url, "-o", str(temporary)], check=True)
    if sha1 and hashlib.sha1(temporary.read_bytes()).hexdigest() != sha1:
        temporary.unlink()
        raise RuntimeError(f"SHA-1 mismatch: {path}")
    temporary.replace(path)


def main():
    manifest = CACHE / "manifest.json"
    fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", manifest)
    version = next(v for v in json.loads(manifest.read_text())["versions"]
                   if v["id"] == "26.4-snapshot-3")
    metadata = CACHE / "version.json"
    fetch(version["url"], metadata, version["sha1"])
    data = json.loads(metadata.read_text())
    assert data["id"] == "26.4-snapshot-3"
    client = data["downloads"]["client"]
    fetch(client["url"], CACHE / "client.jar", client["sha1"])
    artifacts = [lib["downloads"]["artifact"] for lib in data["libraries"]
                 if "artifact" in lib.get("downloads", {})]
    jobs = []
    for artifact in artifacts:
        path = Path(artifact["path"])
        assert not path.is_absolute() and ".." not in path.parts
        jobs.append((artifact["url"], CACHE / "libraries" / path, artifact["sha1"]))
    for coordinate in (
        "com/google/guava/guava/33.4.8-jre/guava-33.4.8-jre.jar",
        "com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
        "org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar",
    ):
        url = "https://repo.maven.apache.org/maven2/" + coordinate
        path = BASE / "test-deps" / coordinate
        digest = path.with_suffix(".jar.sha1")
        fetch(url + ".sha1", digest)
        expected = digest.read_text().strip()
        assert len(expected) == 40 and all(c in "0123456789abcdef" for c in expected)
        jobs.append((url, path, expected))
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda job: fetch(*job), jobs))
    print(f"Verified Snapshot 3 client and {len(jobs)} dependency artifacts")


if __name__ == "__main__":
    main()
