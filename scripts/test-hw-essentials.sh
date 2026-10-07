#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Requires hw-essentials JAR and API classes from build script
if [[ ! -f dist/hw-essentials-0.1.0.jar ]]; then
  echo "Mod JAR not found. Run ./scripts/build-hw-essentials.sh first" >&2
  exit 1
fi

mkdir -p out/test-classes out/test-libraries

# Test dependency only; production uses Minecraft's own Brigadier
curl --fail --location --retry 3 -o out/test-libraries/brigadier.jar https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar

# Need API classes - rebuild them
API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
if [[ ! -d "$API_SOURCE_DIR" ]]; then
  echo "HW-CodaLoader API sources not found at $API_SOURCE_DIR" >&2
  exit 1
fi
mkdir -p out/api-classes
mapfile -d '' API_SOURCES < <(find "$API_SOURCE_DIR" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d out/api-classes "${API_SOURCES[@]}"

# Compile tests
mapfile -d '' TEST_SOURCES < <(find tests/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/api-classes:dist/hw-essentials-0.1.0.jar:out/test-libraries/brigadier.jar -d out/test-classes "${TEST_SOURCES[@]}"

TEST_CP=out/test-classes:out/api-classes:dist/hw-essentials-0.1.0.jar:out/test-libraries/brigadier.jar
java -cp "$TEST_CP" dev.howlingwhispers.essentials.EssentialsTest