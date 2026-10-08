#!/usr/bin/env bash
# Fetch ORIGINAL BuildCraft artwork/models/recipes, pinned to a fixed commit.
# This source is the canonical BuildCraft/BuildCraft repository, NEVER
# BuildCraftRefabricated. Run in the BuildCraft mod build, not on user machines.
set -euo pipefail

UPSTREAM_REPO="https://github.com/BuildCraft/BuildCraft.git"
UPSTREAM_REV="7c86626d09c4569fb2e0f458a16aaf8c3148ebaf"
OUTPUT_DIR="${1:?Usage: bash scripts/import-buildcraft-upstream-assets.sh OUTPUT_DIRECTORY}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git -C "$work" init -q upstream
git -C "$work/upstream" remote add origin "$UPSTREAM_REPO"
git -C "$work/upstream" sparse-checkout init --cone
git -C "$work/upstream" sparse-checkout set buildcraft_resources/assets
git -C "$work/upstream" -c protocol.version=2 fetch -q --depth 1 --filter=blob:none origin "$UPSTREAM_REV"
git -C "$work/upstream" -c advice.detachedHead=false checkout -q --detach FETCH_HEAD

resolved="$(git -C "$work/upstream" rev-parse HEAD)"
[[ "$resolved" == "$UPSTREAM_REV" ]] || {
  echo "Refusing unpinned BuildCraft artwork revision $resolved" >&2
  exit 1
}

upstream="$work/upstream"
assets="$upstream/buildcraft_resources/assets"
[[ -f "$assets/buildcrafttransport/textures/pipes/wood_item_clear.png" ]]
[[ -f "$assets/buildcrafttransport/textures/pipes/cobblestone_item.png" ]]
[[ -f "$assets/buildcraftcore/textures/items/wrench.png" ]]
[[ -f "$assets/buildcraftcore/models/item/engine_redstone.json" ]]
[[ -f "$upstream/buildcraft_resources/LICENSE.BUILDCRAFT" ]]
[[ -f "$upstream/LICENSE-NEW" ]]

mkdir -p "$OUTPUT_DIR/assets" "$OUTPUT_DIR/META-INF/buildcraft-upstream"
cp -a "$assets/." "$OUTPUT_DIR/assets/"
cp "$upstream/buildcraft_resources/LICENSE.BUILDCRAFT" \
  "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE.BUILDCRAFT"
cp "$upstream/LICENSE-NEW" \
  "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE-NEW"
cp "$upstream/LICENSE" \
  "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE"
cat > "$OUTPUT_DIR/META-INF/buildcraft-upstream/SOURCE.txt" <<EOF
ORIGINAL BuildCraft / BuildCraft
https://github.com/BuildCraft/BuildCraft
Branch: 8.0.x-1.12.2
Immutable source commit: $UPSTREAM_REV
Imported: buildcraft_resources/assets/**
Original source location and applicable licensing notices are preserved.
The included assets are NOT the newly-written CML runtime logic.
Original models target Minecraft 1.12.2; H.O.W.L. still needs a modern
registry and model adaptation before they can appear as playable items.
EOF

echo "Bundled original BuildCraft source assets from pinned revision $UPSTREAM_REV"
