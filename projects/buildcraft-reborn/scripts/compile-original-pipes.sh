#!/usr/bin/env bash
# A real build gate: failure in ANY original dependency fails the build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PROJECT="$ROOT/projects/buildcraft-reborn"
BASE="$ROOT/dist/buildcraft-reborn"
CACHE="$BASE/mojang-26.4-snapshot-3"
mkdir -p "$BASE"
rm -f "$BASE/original-pipes-javac-errors.txt"
SDK="${HW_CODALOADER_API_DIR:-$ROOT/../HW-CodaLoader/src/main/java}"
ENTRIES=()
if [[ "${1:-}" != '--holders-only' ]]; then
  if [[ ! -s "$SDK/dev/howlingwhispers/codaloader/api/CodaContext.java" ]]; then
    echo "H.O.W.L. SDK missing at $SDK; set HW_CODALOADER_API_DIR to its src/main/java directory." >&2
    exit 1
  fi
  ENTRIES+=("$PROJECT/bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java")
fi
python3 "$PROJECT/scripts/prepare-game-deps.py"
python3 "$PROJECT/scripts/stage-original-pipe-holders.py" > "$BASE/staging.log"
CP="$CACHE/client.jar"
while IFS= read -r jar; do CP="$CP:$jar"; done < <(
  find "$CACHE/libraries" "$BASE/test-deps" -type f -name '*.jar' | sort
)
CLASSES="$BASE/original-pipes-classes"
rm -rf "$CLASSES"
mkdir -p "$CLASSES"
trap 'result=$?; if [[ $result -ne 0 ]]; then rm -rf "$CLASSES"; fi' EXIT
STAGED="$BASE/staged-snapshot3/src/main/java"
ORIGINAL="$BASE/effective-1.21.11-neoforge/src/main/java"
BRIDGE="$PROJECT/bridge/src/main/java"
javac --release 25 -proc:none -encoding UTF-8 -Xmaxerrs 10000 -J-Xmx3g \
  -sourcepath "$STAGED:$BRIDGE:$ORIGINAL:$SDK" -cp "$CP" -d "$CLASSES" \
  "$STAGED/buildcraft/transport/block/BlockPipeHolder.java" \
  "$STAGED/buildcraft/transport/tile/TilePipeHolder.java" \
  "${ENTRIES[@]}" \
  2> "$BASE/original-pipes-javac-errors.txt"
test -s "$CLASSES/buildcraft/transport/block/BlockPipeHolder.class"
test -s "$CLASSES/buildcraft/transport/tile/TilePipeHolder.class"
echo 'Original pipe build compiled. World testing is still required.'
