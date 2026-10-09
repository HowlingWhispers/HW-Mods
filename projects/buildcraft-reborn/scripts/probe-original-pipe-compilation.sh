#!/usr/bin/env bash
# Compile/probe original staged BCCE pipe holders against Mojang Snapshot 3.
# No placeholder gameplay or installable JAR is ever generated here.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BASE="$ROOT/dist/buildcraft-reborn"
STAGED="$BASE/staged-snapshot3/src/main/java"
ORIGINAL="$BASE/effective-1.21.11-neoforge/src/main/java"
CACHE="$BASE/mojang-26.4-snapshot-3"
CLIENT="$CACHE/client.jar"
GUAVA="$BASE/test-deps/guava-33.4.8-jre.jar"
REPORT="$BASE/pipe-holder-compiler-report.txt"
ERRORS="$BASE/pipe-holder-javac-errors.txt"
mkdir -p "$BASE"
test -s "$CLIENT"
test -s "$GUAVA"
test -s "$CACHE/version.json"
test -s "$STAGED/buildcraft/transport/tile/TilePipeHolder.java"

# Use verified Mojang dependencies to isolate actual NeoForge/API port blockers.
LIBROOT="$CACHE/libraries"
mkdir -p "$LIBROOT"
jq -r '.libraries[].downloads.artifact | select(.url != null) |
  [.url, .path, .sha1] | join("|")' "$CACHE/version.json" > "$BASE/mojang-libraries-list.txt"
while IFS='|' read -r url artifact expected; do
  [[ "$artifact" != /* && "$artifact" != *".."* && "$expected" =~ ^[0-9a-f]{40}$ ]] || {
    echo "Unsafe Mojang library entry: $artifact" >&2
    exit 1
  }
  target="$LIBROOT/$artifact"
  mkdir -p "$(dirname "$target")"
  if [[ ! -s "$target" ]]; then
    curl --fail --location --silent --show-error --retry 3 "$url" -o "$target.tmp"
    mv "$target.tmp" "$target"
  fi
  [[ "$(sha1sum "$target" | awk '{print $1}')" == "$expected" ]] || {
    echo "Mojang library checksum mismatch: $artifact" >&2
    exit 1
  }
done < "$BASE/mojang-libraries-list.txt"
CLASSPATH="$CLIENT:$GUAVA"
while IFS= read -r jar; do CLASSPATH="$CLASSPATH:$jar"; done < <(
  find "$LIBROOT" -type f -name '*.jar' | sort
)
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

set +e
timeout 120s javac --release 25 -proc:none -Xmaxerrs 70 -J-Xmx1536m \
  -sourcepath "$STAGED:$ORIGINAL" -cp "$CLASSPATH" -d "$BUILD" \
  "$STAGED/buildcraft/transport/block/BlockPipeHolder.java" \
  "$STAGED/buildcraft/transport/tile/TilePipeHolder.java" \
  > "$BASE/pipe-holder-javac-stdout.txt" 2>"$ERRORS"
RESULT=$?
set -e

if [[ $RESULT -eq 0 ]]; then
  printf 'BLOCK_PIPE_HOLDER=COMPILED\nTILE_PIPE_HOLDER=COMPILED\n' > "$REPORT"
  echo 'PASS: staged original BCCE pipe holders compiled against Mojang Snapshot 3.'
else
  {
    echo 'BLOCK_PIPE_HOLDER=NOT_COMPILED'
    echo 'TILE_PIPE_HOLDER=NOT_COMPILED'
    echo "JAVAC_EXIT=$RESULT"
    echo "OFFICIAL_MOJANG_LIBRARIES=$(find "$LIBROOT" -name '*.jar' | wc -l)"
    echo 'TARGET=official Mojang Minecraft 26.4 Snapshot 3'
    echo 'SOURCE=original BCCE 8.0.23 plus 3 reversible compatibility edits'
    echo 'BLOCK_SOUND_CLASSES:'
    jar tf "$CLIENT" | grep -i 'sound' | grep -i 'block' | head -n 12 || true
    echo 'BLOCK_PROPERTIES_SOUND_METHODS:'
    javap -p -classpath "$CLASSPATH" 'net.minecraft.world.level.block.state.BlockBehaviour$Properties' 2>/dev/null |
      grep -iE 'sound|Properties' | head -n 17 || true
    echo 'BLOCK_SOUND_REGISTRIES:'
    javap -p -classpath "$CLASSPATH" net.minecraft.core.registries.BuiltInRegistries 2>/dev/null |
      grep -i 'sound' | head -n 12 || true
    echo "NEOFORGE_MISSING=$(grep -c 'package net.neoforged' "$ERRORS" || true)"
    echo "IMPORT_ERRORS=$(grep -c 'error: package .* does not exist' "$ERRORS" || true)"
    echo "MISSING_SYMBOLS=$(grep -c 'error: cannot find symbol' "$ERRORS" || true)"
    echo 'FIRST_COMPILER_ERRORS:'
    awk '/error:|symbol:|location:|cannot access|module not found/ {
      print
      if (++n >= 55) exit
    }' "$ERRORS"
    echo 'NEXT=port original BCCE NeoForge capability/render base dependencies'
  } > "$REPORT"
  echo 'BLOCKED: original BCCE pipe holders do not yet compile against HOWL.'
fi
cat "$REPORT"
echo 'Full javac diagnostics archived. No installable mod JAR was produced.'
