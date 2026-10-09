#!/usr/bin/env bash
# Integration fixture against the current native H.O.W.L. API; no Minecraft blocks are claimed.
set -euo pipefail
cd "$(dirname "$0")/.."
API_SOURCE_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
API_DIR="$API_SOURCE_DIR/dev/howlingwhispers/codaloader/api"
[[ -d "$API_DIR" ]] || { echo "Missing H.O.W.L. API at $API_DIR" >&2; exit 1; }
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/api" "$TMP_DIR/test"
mapfile -d '' API_SOURCES < <(find "$API_DIR" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$TMP_DIR/api" "${API_SOURCES[@]}"
mapfile -d '' MOD_SOURCES < <(find mods/buildcraft-cml/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$TMP_DIR/api" -d "$TMP_DIR/test" "${MOD_SOURCES[@]}" tests/buildcraft-cml/BuildCraftRuntimeTest.java tests/buildcraft-cml/BuildCraftGlassPipeDemoTest.java tests/buildcraft-cml/BuildCraftEngineRuntimeTest.java tests/buildcraft-cml/BuildCraftEnginePersistenceTest.java tests/buildcraft-cml/BuildCraftRedstoneEngineTest.java tests/buildcraft-cml/BuildCraftMjPortAdapterTest.java tests/buildcraft-cml/BuildCraftNativeRouteTest.java
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftRuntimeTest
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftGlassPipeDemoTest

java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftEngineRuntimeTest
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftEnginePersistenceTest
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftRedstoneEngineTest
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftMjPortAdapterTest
java -cp "$TMP_DIR/api:$TMP_DIR/test" BuildCraftNativeRouteTest
