#!/usr/bin/env bash
# Prepare the unmodified BuildCraft Community Edition source as a porting reference.
# This does NOT compile a NeoForge JAR or claim H.O.W.L. gameplay compatibility.
set -euo pipefail
cd "$(dirname "$0")/.."

readonly BCCE_VERSION="8.0.23"
readonly BCCE_COMMIT="23c6af379676ce5262c5c0cb6f1f331edc9b12c6"
readonly BCCE_REPOSITORY="https://github.com/BCCE-team/BuildCraft"
readonly BCCE_TARGET="1.21.11-neoforge"
readonly CACHE_ROOT="${HW_BUILDCRAFT_UPSTREAM_CACHE:-dist/buildcraft-upstream}"
readonly SOURCE="$CACHE_ROOT/bcce-$BCCE_COMMIT"
readonly OUTPUT="dist/buildcraft-reference/$BCCE_TARGET"

mkdir -p "$CACHE_ROOT" dist/buildcraft-reference
if [[ ! -d "$SOURCE/.git" ]]; then
  if [[ -e "$SOURCE" ]]; then
    echo "Refusing to overwrite unexpected source cache: $SOURCE" >&2
    exit 1
  fi
  temp_source="$(mktemp -d "$CACHE_ROOT/.bcce-XXXXXX")"
  trap 'rm -rf "$temp_source"' EXIT
  git -C "$temp_source" init -q
  git -C "$temp_source" fetch -q --depth 1 "$BCCE_REPOSITORY.git" "$BCCE_COMMIT"
  git -C "$temp_source" checkout --detach -q FETCH_HEAD
  [[ "$(git -C "$temp_source" rev-parse HEAD)" == "$BCCE_COMMIT" ]]
  mv "$temp_source" "$SOURCE"
  trap - EXIT
fi

if [[ "$(git -C "$SOURCE" rev-parse HEAD)" != "$BCCE_COMMIT" ]] ||
   [[ -n "$(git -C "$SOURCE" status --porcelain --untracked-files=all)" ]]; then
  echo "BCCE cache is not the exact clean release source; refusing to proceed" >&2
  exit 1
fi
grep -Fxq "common.mod.version=$BCCE_VERSION" "$SOURCE/build-config/common.properties"
grep -Fxq "target.$BCCE_TARGET.deps.minecraft=1.21.11" "$SOURCE/build-config/targets.properties"
test -f "$SOURCE/LICENSE.txt"
test -f "$SOURCE/scripts/source_layout.py"

# Use BCCE's OWN authoritative layered-source materializer. Never concatenate
# arbitrary source folders or mistake the generated NeoForge port for H.O.W.L.
python3 "$SOURCE/scripts/source_layout.py" "$BCCE_TARGET" --output "$PWD/$OUTPUT"

for module in lib core energy transport factory silicon builders robotics; do
  if [[ ! -d "$OUTPUT/src/main/java/buildcraft/$module" ]]; then
    echo "Missing original BCCE gameplay module: $module" >&2
    exit 1
  fi
done
test -s "$OUTPUT/src/main/java/buildcraft/transport/BCTransportPipes.java"
test -s "$OUTPUT/src/main/java/buildcraft/transport/BCTransportRegistries.java"
test -s "$OUTPUT/src/main/java/buildcraft/energy/BCEnergyBlocks.java"
test -d "$OUTPUT/src/main/resources/assets"

mkdir -p "$OUTPUT/META-INF/buildcraft-upstream"
cp "$SOURCE/LICENSE.txt" "$OUTPUT/META-INF/buildcraft-upstream/LICENSE.txt"
python3 - "$OUTPUT" "$BCCE_VERSION" "$BCCE_COMMIT" "$BCCE_REPOSITORY" "$BCCE_TARGET" <<'PY'
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])
version, commit, repository, target = sys.argv[2:]
java = root / "src/main/java/buildcraft"
resources = root / "src/main/resources"
modules = ["lib", "core", "energy", "transport", "factory", "silicon", "builders", "robotics"]
counts = {m: sum(1 for _ in (java / m).rglob("*.java")) for m in modules}
if any(count == 0 for count in counts.values()):
    raise SystemExit("BCCE reference contains an empty gameplay module")
manifest = {
    "upstream": repository,
    "version": version,
    "release_tag": version,
    "commit": commit,
    "source_url": f"{repository}/tree/{commit}",
    "reference_target": target,
    "port_target": "26.4-snapshot-3/H.O.W.L.",
    "status": "unmodified effective BCCE reference; NOT a H.O.W.L. binary",
    "java_sources": len(list(java.rglob("*.java"))),
    "resource_files": sum(1 for p in resources.rglob("*") if p.is_file()),
    "module_sources": counts,
}
(root / "HOWL-SOURCE-MANIFEST.json").write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
)
print(f"Materialized BCCE {version} @ {commit[:12]} to {root}")
print("Module Java sources:", counts)
print("Reference only: H.O.W.L. block entities, rendering and gameplay still require a native port.")
PY
