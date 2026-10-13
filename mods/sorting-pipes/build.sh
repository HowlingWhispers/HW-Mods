#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
API_JAR="${1:-${HW_CODALOADER_API_JAR:-lib/howl-api-sorting-pipes.jar}}"
if [[ ! -f "$API_JAR" ]]; then
  API_DIR="${HW_CODALOADER_API_DIR:-../../../HW-CodaLoader/src/main/java}"
  [[ -d "$API_DIR/dev/howlingwhispers/codaloader/api" ]] || { echo 'Set HW_CODALOADER_API_DIR to H.O.W.L. 0.0.38 API sources or pass its API JAR' >&2; exit 1; }
  mkdir -p build/api lib
  mapfile -d '' API_SOURCES < <(find "$API_DIR/dev/howlingwhispers/codaloader/api" -name '*.java' -print0)
  javac --release 21 -d build/api "${API_SOURCES[@]}"
  jar --create --file lib/howl-api-sorting-pipes.jar -C build/api .
  API_JAR=lib/howl-api-sorting-pipes.jar
fi
mkdir -p build/classes dist
find build/classes -type f -delete
mapfile -d '' SOURCES < <(find src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$API_JAR" -d build/classes "${SOURCES[@]}"
python3 generate-resources.py
cp -R resources/. build/classes/
cp dist/HOWL-Sorting-Pipes-Resources.zip build/classes/sorting-pipes-resources.zip
jar --create --file dist/howl-sorting-pipes-1.0.2.jar -C build/classes .
echo 'Built dist/howl-sorting-pipes-1.0.2.jar'
