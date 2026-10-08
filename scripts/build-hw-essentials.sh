#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

MOD_VERSION="0.2.0"
MINECRAFT_VERSION="26.4-snapshot-3"

rm -rf out dist
mkdir -p out/classes dist

# Compile API from HW-CodaLoader (expected to be built first or available as dependency)
# For standalone build, we compile the API locally from a checked-out HW-CodaLoader
API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
API_PACKAGE_DIR="$API_SOURCE_DIR/dev/howlingwhispers/codaloader/api"
if [[ ! -d "$API_PACKAGE_DIR" ]]; then
  echo "HW-CodaLoader API sources not found at $API_SOURCE_DIR" >&2
  echo "Set HW_CODALOADER_API_DIR or check out HW-CodaLoader alongside HW-Mods" >&2
  exit 1
fi

# Only the public SDK belongs on a mod's compile classpath.
# Pulling in the whole loader would wrongly require ASM/Minecraft internals.
mapfile -d '' API_SOURCES < <(find "$API_PACKAGE_DIR" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d out/classes "${API_SOURCES[@]}"

# Compile HW Essentials
mapfile -d '' MOD_SOURCES < <(find mods/hw-essentials/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/classes -d out/mod-classes "${MOD_SOURCES[@]}"
cp mods/hw-essentials/resources/coda.mod.json out/mod-classes/

# Package mod JAR
jar --create --file "dist/hw-essentials-${MOD_VERSION}.jar" -C out/mod-classes .

echo "Built: dist/hw-essentials-${MOD_VERSION}.jar"

# Verify JAR contents
jar tf "dist/hw-essentials-${MOD_VERSION}.jar" | grep -F "coda.mod.json"
jar tf "dist/hw-essentials-${MOD_VERSION}.jar" | grep -F "HwEssentialsMod.class"

# Generate SHA256
sha256sum "dist/hw-essentials-${MOD_VERSION}.jar" > "dist/hw-essentials-${MOD_VERSION}.jar.sha256"
cat "dist/hw-essentials-${MOD_VERSION}.jar.sha256"