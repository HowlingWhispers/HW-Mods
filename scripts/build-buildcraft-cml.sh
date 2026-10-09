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
# Legacy JAR basename is required by current CodaLauncher Nightly profiles.
# The authoritative mod version is 0.0.1-dev.1 inside coda.mod.json.
jar --create --file dist/buildcraft-cml-0.1.0-dev.jar -C "$TMP_DIR/mod" .
sha256sum dist/buildcraft-cml-0.1.0-dev.jar > dist/buildcraft-cml-0.1.0-dev.jar.sha256
echo 'Built H.O.W.L. BuildCraft 0.0.1-dev.1 (legacy JAR filename; BCCE 8.0.23 engine and real pipe route scanner; item transport pending)'
