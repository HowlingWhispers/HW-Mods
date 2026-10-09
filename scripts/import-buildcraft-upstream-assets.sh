#!/usr/bin/env bash
# BCCE is the selected source. Forge/NeoForge binaries never run in H.O.W.L.
set -euo pipefail
RELEASE="8.0.23"
# Exact commit tagged 8.0.23 upstream. Keep in sync with prepare-buildcraft-bcce.sh.
COMMIT="23c6af379676ce5262c5c0cb6f1f331edc9b12c6"
PROJECT="https://github.com/BCCE-team/BuildCraft"
OUTPUT_DIR="${1:?Usage: bash scripts/import-buildcraft-upstream-assets.sh OUTPUT_DIRECTORY}"
CACHE_ROOT="${HW_BUILDCRAFT_UPSTREAM_CACHE:-dist/buildcraft-upstream}"
SOURCE="$CACHE_ROOT/bcce-$COMMIT"
mkdir -p "$CACHE_ROOT" "$OUTPUT_DIR/META-INF/buildcraft-upstream"
if [[ ! -d "$SOURCE/.git" ]]; then
  TEMP_SOURCE="$(mktemp -d "$CACHE_ROOT/.bcce-source-XXXXXX")"
  trap 'rm -rf "$TEMP_SOURCE"' EXIT
  git -C "$TEMP_SOURCE" init -q
  git -C "$TEMP_SOURCE" fetch --depth 1 "$PROJECT.git" "$COMMIT"
  git -C "$TEMP_SOURCE" checkout --detach -q FETCH_HEAD
  [[ "$(git -C "$TEMP_SOURCE" rev-parse HEAD)" == "$COMMIT" ]]
  mv "$TEMP_SOURCE" "$SOURCE"
  trap - EXIT
fi
[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$COMMIT" ]] || {
  echo "BCCE source commit does not match the pinned baseline" >&2; exit 1;
}
[[ -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)" ]] || {
  echo "Cached BCCE source was edited; refusing unverified assets" >&2; exit 1;
}
grep -Fx "common.mod.version=$RELEASE" "$SOURCE/build-config/common.properties"
# Match BCCE's modern 1.21.11 source-layer precedence for resources only.
# Platform Java classes are porting reference, never imported as binaries.
for layer in source-shared source-families/modern source-platforms/neoforge \
  source-family-platforms/modern/neoforge version-src/1.21.11-neoforge
do
  assets="$SOURCE/$layer/src/main/resources/assets"
  if [[ -d "$assets" ]]; then cp -R "$assets" "$OUTPUT_DIR/"; fi
done
for entry in \
  assets/buildcrafttransport/textures/pipes/wood_item_clear.png \
  assets/buildcrafttransport/textures/pipes/cobblestone_item.png \
  assets/buildcraftcore/textures/items/wrench.png \
  assets/buildcraftcore/textures/blocks/engine/wood/back.png \
  assets/buildcraftlib/textures/blocks/engine/trunk_blue.png
do
  test -s "$OUTPUT_DIR/$entry"
  cmp "$SOURCE/source-shared/src/main/resources/$entry" "$OUTPUT_DIR/$entry"
done
cp "$SOURCE/LICENSE.txt" "$OUTPUT_DIR/META-INF/buildcraft-upstream/LICENSE.txt"
cat > "$OUTPUT_DIR/META-INF/buildcraft-upstream/SOURCE.txt" <<EOF
UPSTREAM: BUILDCRAFT COMMUNITY EDITION
Version: $RELEASE (repository source baseline, not a H.O.W.L. compatibility claim)
Project: $PROJECT
Pinned commit: $COMMIT
Corresponding source: $PROJECT/tree/$COMMIT
Source archive: $PROJECT/archive/$COMMIT.zip
Reference target: Minecraft 1.21.11 / NeoForge
H.O.W.L. target: Minecraft 26.4 Snapshot 3
Imported unchanged: original resource assets from BCCE's modern source layers.
NOT imported: Forge/NeoForge binaries, entrypoints or runtime dependencies.
This early H.O.W.L. transport adapter is not full BCCE feature parity.
License: MPL-2.0; retain per-file SpaceToad/BuildCraft/BCCE copyright notices.
EOF
echo "Imported BCCE $RELEASE assets from verified source commit $COMMIT"
