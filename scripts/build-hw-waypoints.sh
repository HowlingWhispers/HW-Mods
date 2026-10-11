#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="0.1.0-dev.1"
OUT="out/hw-waypoints"
mkdir -p "$OUT" dist
rm -rf "$OUT/api" "$OUT/classes"
mkdir -p "$OUT/api" "$OUT/classes"

# Compile against the SDK only; never package API classes in the addon.
if [[ -n "${HW_CODALOADER_API_JAR:-}" ]]; then
  API_CP="$HW_CODALOADER_API_JAR"
else
  API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
  API_PACKAGE_DIR="$API_SOURCE_DIR/dev/howlingwhispers/codaloader/api"
  if [[ ! -d "$API_PACKAGE_DIR" ]]; then
    echo "H.O.W.L. SDK API not found. Set HW_CODALOADER_API_JAR or HW_CODALOADER_API_DIR." >&2
    exit 1
  fi
  mapfile -d '' api_sources < <(find "$API_PACKAGE_DIR" -name '*.java' -print0)
  javac --release 21 -encoding UTF-8 -d "$OUT/api" "${api_sources[@]}"
  API_CP="$OUT/api"
fi
mapfile -d '' sources < <(find mods/hw-waypoints/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$API_CP" -d "$OUT/classes" "${sources[@]}"
cp -R mods/hw-waypoints/resources/. "$OUT/classes/"
jar --create --file "dist/hw-waypoints-${VERSION}.jar" -C "$OUT/classes" .
jar tf "dist/hw-waypoints-${VERSION}.jar" | grep -F 'coda.mod.json'
if jar tf "dist/hw-waypoints-${VERSION}.jar" | grep -q 'dev/howlingwhispers/codaloader/api/'; then
  echo "ERROR: private loader API copy included" >&2
  exit 1
fi
sha256sum "dist/hw-waypoints-${VERSION}.jar" > "dist/hw-waypoints-${VERSION}.jar.sha256"
echo "Built dist/hw-waypoints-${VERSION}.jar"
