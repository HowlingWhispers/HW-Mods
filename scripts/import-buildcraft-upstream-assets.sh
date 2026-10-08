#!/usr/bin/env bash
# PORT ORIGINAL RELEASE, not the moving development branch or a recreation.
# Official release: BuildCraft 8.0.0 (Minecraft 1.12.2, 2025-04-02).
# Source and binary: https://mod-buildcraft.com/releases/BuildCraft/8.0.0/
# We extract resource assets only. The original Forge bytecode CANNOT run in H.O.W.L.
set -euo pipefail

RELEASE="8.0.0"
# Actual SHA-256 digests obtained from the official BuildCraft 8.0.0
# binary/source artifacts in our Linux CI (Oct 8, 2026), NOT calculated
# from a moving Git branch. The build refuses upstream replacement.
BIN_SHA256="f617279f8148a9140ab86c0e4aa4c54fe8a94515d2cf487a47cbd928160b88aa"
SRC_SHA256="63458fee5041e394a58e47d59872c1e0dc514f16ec92ef6d22351daed3db1241"
BASE="https://mod-buildcraft.com/releases/BuildCraft/$RELEASE"
MAVEN="https://mod-buildcraft.com/maven/com/mod-buildcraft/buildcraft-all/$RELEASE"
OUTPUT_DIR="${1:?Usage: bash scripts/import-buildcraft-upstream-assets.sh OUTPUT_DIRECTORY}"
CACHE_DIR="${HW_BUILDCRAFT_UPSTREAM_CACHE:-dist/buildcraft-upstream/$RELEASE}"
mkdir -p "$CACHE_DIR" "$OUTPUT_DIR/META-INF/buildcraft-upstream"
CACHE_DIR="$(cd "$CACHE_DIR" && pwd)"
OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"

# SHA1 files are published with BuildCraft's official Maven artifacts.
# Do not substitute BuildCraft's latest GitHub branch (an unreleased moving
# target) or a third-party recreation. The official artifact names are stable.
fetch_release() {
  local name="$1"
  local artifact="$CACHE_DIR/$name"
  local hash_file="$CACHE_DIR/$name.sha1"
  curl --fail --location --silent --show-error --retry 3 --retry-delay 2 \
    --max-time 150 "$MAVEN/$name.sha1" --output "$hash_file"
  local expected
  expected="$(tr -d '\\r\\n ' < "$hash_file")"
  [[ "$expected" =~ ^[a-fA-F0-9]{40}$ ]] || {
    echo "Invalid official BuildCraft SHA1 for $name" >&2
    return 1
  }
  if [[ ! -f "$artifact" ]] || \
     [[ "$(sha1sum "$artifact" | awk '{print $1}')" != "${expected,,}" ]]; then
    rm -f "$artifact"
    local temp="$artifact.download"
    rm -f "$temp"
    curl --fail --location --silent --show-error --retry 3 --retry-delay 2 \
      --max-time 180 "$BASE/$name" --output "$temp"
    [[ "$(sha1sum "$temp" | awk '{print $1}')" == "${expected,,}" ]] || {
      rm -f "$temp"
      echo "Official BuildCraft $name checksum mismatch" >&2
      return 1
    }
    mv "$temp" "$artifact"
  fi
  echo "BuildCraft $RELEASE official $name SHA256: $(sha256sum "$artifact" | awk '{print $1}')"
}

BIN="buildcraft-all-$RELEASE.jar"
SRC="buildcraft-all-$RELEASE-sources.jar"
fetch_release "$BIN"
fetch_release "$SRC"
printf '%s  %s\n' "$BIN_SHA256" "$CACHE_DIR/$BIN" | sha256sum --check --status || {
  echo "Refusing unexpected BuildCraft 8.0.0 binary: pinned SHA256 failed" >&2
  exit 1
}
printf '%s  %s\n' "$SRC_SHA256" "$CACHE_DIR/$SRC" | sha256sum --check --status || {
  echo "Refusing unexpected BuildCraft 8.0.0 source: pinned SHA256 failed" >&2
  exit 1
}

# Source JAR is the baseline for every ported behavior, not just a license
# notice. CI checks the release source exists and is not a class-only binary.
unzip -Z1 "$CACHE_DIR/$SRC" | grep -qE '(^|/)\.java$|\.java$' || {
  echo "Official BuildCraft $RELEASE source archive contains no Java source" >&2
  exit 1
}
# The 8.0 release should contain canonical wood/cobble, wrench and redstone
# engine textures/models. Fail rather than silently importing old branch art.
for entry in \
  assets/buildcrafttransport/textures/pipes/wood_item_clear.png \
  assets/buildcrafttransport/textures/pipes/cobblestone_item.png \
  assets/buildcraftcore/textures/items/wrench.png \
  assets/buildcraftcore/models/item/engine_redstone.json
do
  unzip -Z1 "$CACHE_DIR/$BIN" | grep -Fxq "$entry" || {
    echo "BuildCraft $RELEASE release missing original asset: $entry" >&2
    exit 1
  }
done

# Original assets are copied into the mod JAR unchanged. No Forge .class
# files or source code from upstream are bundled into the runtime.
(cd "$OUTPUT_DIR" && unzip -oq "$CACHE_DIR/$BIN" 'assets/*')
[[ -f "$OUTPUT_DIR/assets/buildcrafttransport/textures/pipes/wood_item_clear.png" ]]

# Release is from the BuildCraft project itself. Copy its own license
# resources where present, plus upstream LICENSE and LICENSE-NEW, since
# historical assets and code in BuildCraft span both old MMPL and MPL-2.0.
for license in LICENSE LICENSE-NEW; do
  case "$license" in
    LICENSE) local_source="https://raw.githubusercontent.com/BuildCraft/BuildCraft/8.0.x-1.12.2/LICENSE";;
    LICENSE-NEW) local_source="https://raw.githubusercontent.com/BuildCraft/BuildCraft/8.0.x-1.12.2/LICENSE-NEW";;
  esac
  curl --fail --location --silent --show-error --retry 3 \
    "$local_source" --output "$OUTPUT_DIR/META-INF/buildcraft-upstream/$license"
done
if unzip -Z1 "$CACHE_DIR/$BIN" | grep -Fxq 'LICENSE.BUILDCRAFT'; then
  unzip -p "$CACHE_DIR/$BIN" LICENSE.BUILDCRAFT > "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE.BUILDCRAFT"
else
  curl --fail --location --silent --show-error --retry 3 \
    "https://raw.githubusercontent.com/BuildCraft/BuildCraft/8.0.x-1.12.2/buildcraft_resources/LICENSE.BUILDCRAFT" \
    --output "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE.BUILDCRAFT"
fi

cat > "$OUTPUT_DIR/META-INF/buildcraft-upstream/SOURCE.txt" <<EOF
UPSTREAM: ORIGINAL BUILDCRAFT
Release: 8.0.0 (official stable, 2025-04-02; Minecraft 1.12.2)
Project: https://github.com/BuildCraft/BuildCraft
Official release: $BASE/
Official original binary: $BASE/$BIN
Official original source: $BASE/$SRC
Binary SHA1: $(tr -d '\r\n ' < "$CACHE_DIR/$BIN.sha1")
Source SHA1: $(tr -d '\r\n ' < "$CACHE_DIR/$SRC.sha1")
Binary SHA256: $(sha256sum "$CACHE_DIR/$BIN" | awk '{print $1}')
Source SHA256: $(sha256sum "$CACHE_DIR/$SRC" | awk '{print $1}')
Imported unchanged: assets/** from official $BIN
NOT imported: original Forge binaries or unsupported Minecraft 1.12.2 code.
BuildCraft 8.0.0 is the behavioral baseline. The original Minecraft/Forge
registries and rendering still need to be adapted to H.O.W.L. Snapshot 3.
Preserve copyright notices; distribute license terms and corresponding source.
EOF
echo "IMPORTED OFFICIAL BuildCraft $RELEASE assets; original sources cached at $CACHE_DIR/$SRC"
