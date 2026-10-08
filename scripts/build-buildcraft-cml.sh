#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
API_PACKAGE_DIR="$API_SOURCE_DIR/dev/howlingwhispers/codaloader/api"
if [[ ! -d "$API_PACKAGE_DIR" ]]; then
  echo "CodaLoader API sources missing at $API_PACKAGE_DIR" >&2
  echo "Set HW_CODALOADER_API_DIR to HW-CodaLoader/src/main/java." >&2
  exit 1
fi
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/api" "$TMP_DIR/mod" dist
mapfile -d '' API_SOURCES < <(find "$API_PACKAGE_DIR" -name '*.java' -print0)
mapfile -d '' MOD_SOURCES < <(find mods/buildcraft-cml/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$TMP_DIR/api" "${API_SOURCES[@]}"
javac --release 21 -encoding UTF-8 -cp "$TMP_DIR/api" -d "$TMP_DIR/mod" "${MOD_SOURCES[@]}"
cp mods/buildcraft-cml/resources/coda.mod.json "$TMP_DIR/mod/"
# Preserve every original upstream BuildCraft asset rather than drawing
# substitute textures or using third-party recreation packs.
bash scripts/import-buildcraft-upstream-assets.sh "$TMP_DIR/mod"
jar --create --file dist/buildcraft-cml-0.1.0-dev.jar -C "$TMP_DIR/mod" .
sha256sum dist/buildcraft-cml-0.1.0-dev.jar > dist/buildcraft-cml-0.1.0-dev.jar.sha256
echo 'Built dist/buildcraft-cml-0.1.0-dev.jar (original BuildCraft assets bundled; Minecraft block/item registration not yet implemented)'
