#!/usr/bin/env bash
# Compile the selected original item-pipe mechanics and their real dependencies.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PROJECT="$ROOT/projects/buildcraft-reborn"
BASE="$ROOT/dist/buildcraft-reborn"
CACHE="$BASE/mojang-26.4-snapshot-3"
mkdir -p "$BASE"
CLASSES="$BASE/original-pipes-classes"
rm -rf "$CLASSES"
trap 'result=$?; if [[ $result -ne 0 ]]; then rm -rf "$CLASSES"; fi' EXIT
rm -f "$BASE/original-pipes-javac-errors.txt"
SDK="${HW_CODALOADER_API_DIR:-$ROOT/../HW-CodaLoader/src/main/java}"
ENTRIES=()
SCOPE_ARGS=()
HOLDERS_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --holders-only) HOLDERS_ONLY=true ;;
    --with-diamond) SCOPE_ARGS+=("$arg") ;;
    *) echo "Unknown build argument: $arg" >&2; exit 2 ;;
  esac
done
if [[ "$HOLDERS_ONLY" == false ]]; then
  if [[ ! -s "$SDK/dev/howlingwhispers/codaloader/api/CodaContext.java" ]]; then
    echo "H.O.W.L. SDK missing at $SDK; set HW_CODALOADER_API_DIR to its src/main/java directory." >&2
    exit 1
  fi
  ENTRIES+=("$PROJECT/bridge/src/main/java/dev/howlingwhispers/buildcraftreborn/BuildCraftRebornMod.java")
fi
python3 "$PROJECT/scripts/prepare-game-deps.py"
python3 "$PROJECT/scripts/stage-original-item-pipes.py" "${SCOPE_ARGS[@]}" > "$BASE/staging.log"
CP="$CACHE/client.jar"
while IFS= read -r jar; do CP="$CP:$jar"; done < <(
  find "$CACHE/libraries" "$BASE/test-deps" -type f -name '*.jar' | sort
)
mkdir -p "$CLASSES"
STAGED="$BASE/staged-snapshot3/src/main/java"
ORIGINAL="$BASE/effective-1.21.11-neoforge/src/main/java"
BRIDGE="$PROJECT/bridge/src/main/java"
if [[ ${#SCOPE_ARGS[@]} -ne 0 ]]; then
  ENTRIES+=("$ORIGINAL/buildcraft/transport/pipe/behaviour/PipeBehaviourDiamondItem.java")
fi
javac --release 25 -proc:none -encoding UTF-8 -Xmaxerrs 10000 -J-Xmx3g \
  -sourcepath "$STAGED:$BRIDGE:$ORIGINAL:$SDK" -cp "$CP" -d "$CLASSES" \
  "$STAGED/buildcraft/transport/block/BlockPipeHolder.java" \
  "$STAGED/buildcraft/transport/tile/TilePipeHolder.java" \
  "$STAGED/buildcraft/transport/BCTransportRegistries.java" \
  "$STAGED/buildcraft/transport/BCTransportPipes.java" \
  "$STAGED/buildcraft/transport/pipe/behaviour/PipeBehaviourWood.java" \
  "$ORIGINAL/buildcraft/transport/pipe/behaviour/PipeBehaviourStone.java" \
  "$STAGED/buildcraft/transport/pipe/flow/PipeFlowItems.java" \
  "$STAGED/buildcraft/core/blockEntity/TileEngineRedstone_BC8.java" \
  "$ORIGINAL/buildcraft/lib/internal/api/v2/BuildCraftApiRuntimeProvider.java" \
  "$STAGED/buildcraft/lib/internal/mj/MjApi2PlatformBridge.java" \
  "$STAGED/buildcraft/core/block/BlockEngine_BC8.java" \
  "$STAGED/buildcraft/transport/client/render/RenderPipeHolder.java" \
  "$STAGED/buildcraft/core/client/render/RenderEngine_BC8.java" \
  "$STAGED/buildcraft/transport/client/model/ModelPipeNative121111.java" \
  "${ENTRIES[@]}" \
  2> "$BASE/original-pipes-javac-errors.txt"
test -s "$CLASSES/buildcraft/transport/block/BlockPipeHolder.class"
test -s "$CLASSES/buildcraft/transport/tile/TilePipeHolder.class"
for original in \
  transport/pipe/behaviour/PipeBehaviourWood \
  transport/pipe/behaviour/PipeBehaviourStone \
  transport/pipe/flow/PipeFlowItems \
  transport/pipe/flow/TravellingItem \
  core/blockEntity/TileEngineRedstone_BC8; do
  test -s "$CLASSES/buildcraft/$original.class"
done
for original in \
  lib/internal/api/v2/BuildCraftApiRuntimeProvider \
  lib/internal/mj/MjApi2PlatformBridge \
  core/block/BlockEngine_BC8 \
  core/client/render/RenderEngine_BC8 \
  transport/client/render/RenderPipeHolder \
  transport/client/render/PipeFlowRendererItems \
  transport/client/model/ModelPipeNative121111; do
  test -s "$CLASSES/buildcraft/$original.class"
done
if [[ ${#SCOPE_ARGS[@]} -ne 0 ]]; then
  test -s "$CLASSES/buildcraft/transport/pipe/behaviour/PipeBehaviourDiamondItem.class"
fi
echo 'Selected original item-pipe mechanics compiled. Chest-to-chest world testing is still required.'
