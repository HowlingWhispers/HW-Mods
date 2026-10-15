#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
MOD=mods/howl-codex
API_DIR="${HW_CODALOADER_API_DIR:-../HW-CodaLoader/src/main/java}"
if [[ ! -d "$API_DIR/dev/howlingwhispers/codaloader/api" ]]; then
  echo "Set HW_CODALOADER_API_DIR to the matching H.O.W.L. API source folder" >&2
  exit 1
fi
mkdir -p "$MOD/build/api" "$MOD/build/classes" "$MOD/build/tests" "$MOD/dist"
mapfile -d '' API_SOURCES < <(find "$API_DIR/dev/howlingwhispers/codaloader/api" -name '*.java' -print0)
mapfile -d '' MOD_SOURCES < <(find "$MOD/src" -name '*.java' -print0)
mapfile -d '' TEST_SOURCES < <(find "$MOD/tests" -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$MOD/build/api" "${API_SOURCES[@]}"
javac --release 21 -encoding UTF-8 -cp "$MOD/build/api" -d "$MOD/build/classes" "${MOD_SOURCES[@]}"
cp "$MOD/resources/coda.mod.json" "$MOD/build/classes/"
javac --release 21 -encoding UTF-8 -cp "$MOD/build/api:$MOD/build/classes" -d "$MOD/build/tests" "${TEST_SOURCES[@]}"
java -cp "$MOD/build/api:$MOD/build/classes:$MOD/build/tests" dev.howlingwhispers.codex.CodexIndexTest
jar --create --file "$MOD/dist/howl-codex-0.1.0-dev.1.jar" -C "$MOD/build/classes" .
(cd "$MOD/dist" && sha256sum howl-codex-0.1.0-dev.1.jar > howl-codex-0.1.0-dev.1.jar.sha256)
echo "Built $MOD/dist/howl-codex-0.1.0-dev.1.jar (development-only)"
