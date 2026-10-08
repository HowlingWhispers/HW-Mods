#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
if [[ ! -d "$API_SOURCE_DIR" ]]; then
  echo "CodaLoader sources missing. Set HW_CODALOADER_API_DIR to its src/main/java." >&2
  exit 1
fi
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/api" "$TMP_DIR/mod" dist
mapfile -d '' API_SOURCES < <(find "$API_SOURCE_DIR" -name '*.java' -print0)
mapfile -d '' MOD_SOURCES < <(find mods/buildcraft-cml/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$TMP_DIR/api" "${API_SOURCES[@]}"
javac --release 21 -encoding UTF-8 -cp "$TMP_DIR/api" -d "$TMP_DIR/mod" "${MOD_SOURCES[@]}"
cp mods/buildcraft-cml/resources/coda.mod.json "$TMP_DIR/mod/"
jar --create --file dist/buildcraft-cml-0.1.0-dev.jar -C "$TMP_DIR/mod" .
sha256sum dist/buildcraft-cml-0.1.0-dev.jar > dist/buildcraft-cml-0.1.0-dev.jar.sha256
echo 'Built dist/buildcraft-cml-0.1.0-dev.jar (development scaffold, no world blocks yet)'
