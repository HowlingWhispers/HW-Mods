#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PROJECT="$ROOT/projects/buildcraft-reborn"
BASE="$ROOT/dist/buildcraft-reborn"
CACHE="$BASE/mojang-26.4-snapshot-3"
python3 "$PROJECT/scripts/prepare-game-deps.py"
python3 "$PROJECT/scripts/stage-original-pipe-holders.py" > "$BASE/staging.log"
CP="$CACHE/client.jar"
while IFS= read -r jar; do CP="$CP:$jar"; done < <(
  find "$CACHE/libraries" "$BASE/test-deps" -type f -name '*.jar' | sort
)
CLASSES="$BASE/persistence-classes"
rm -rf "$CLASSES"
mkdir -p "$CLASSES"
javac --release 25 -proc:none -encoding UTF-8 -cp "$CP" \
  -sourcepath "$BASE/staged-snapshot3/src/main/java:$PROJECT/bridge/src/main/java:$BASE/effective-1.21.11-neoforge/src/main/java" \
  -d "$CLASSES" \
  "$BASE/staged-snapshot3/src/main/java/buildcraft/lib/compat/minecraft/persistence/BCBlockEntity.java" \
  "$BASE/staged-snapshot3/src/main/java/buildcraft/lib/misc/ChunkUtil.java" \
  "$PROJECT/bridge/src/main/java/buildcraft/lib/compat/howl/storage/IItemHandlerModifiable.java" \
  "$PROJECT/tests/OriginalPersistenceTest.java"
cd "$BASE"
java -ea -cp "$CLASSES:$CP" OriginalPersistenceTest
