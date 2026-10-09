#!/usr/bin/env bash
# Diagnostic compilation of BCCE's actual staged BlockPipeHolder and
# TilePipeHolder against exact Mojang Snapshot 3. This is NOT a release gate:
# a compilation failure is recorded explicitly as BLOCKED until resolved.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BASE="$ROOT/dist/buildcraft-reborn"
STAGED="$BASE/staged-snapshot3/src/main/java"
ORIGINAL="$BASE/effective-1.21.11-neoforge/src/main/java"
CLIENT="$BASE/mojang-26.4-snapshot-3/client.jar"
GUAVA="$BASE/test-deps/guava-33.4.8-jre.jar"
REPORT="$BASE/pipe-holder-compiler-report.txt"
ERRORS="$BASE/pipe-holder-javac-errors.txt"
mkdir -p "$BASE"
test -s "$CLIENT" && test -s "$GUAVA" && test -s "$STAGED/buildcraft/transport/tile/TilePipeHolder.java"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
set +e
timeout 120s javac --release 25 -proc:none -Xmaxerrs 70 -J-Xmx1536m \
  -sourcepath "$STAGED:$ORIGINAL" -cp "$CLIENT:$GUAVA" -d "$BUILD" \
  "$STAGED/buildcraft/transport/block/BlockPipeHolder.java" \
  "$STAGED/buildcraft/transport/tile/TilePipeHolder.java" > "$REPORT.stdout" 2>"$ERRORS"
RESULT=$?
set -e
if [[ $RESULT -eq 0 ]]; then
  {
    echo 'BLOCK_PIPE_HOLDER=COMPILED'
    echo 'TILE_PIPE_HOLDER=COMPILED'
    echo 'ACTION=verify runtime using real Minecraft bootstrap next'
  } > "$REPORT"
  echo 'PASS: original staged BCCE BlockPipeHolder and TilePipeHolder compile on Snapshot 3.'
else
  # This is a deliberately honest blocker report, not a green "compiled" job.
  {
    echo 'BLOCK_PIPE_HOLDER=NOT_COMPILED'
    echo 'TILE_PIPE_HOLDER=NOT_COMPILED'
    echo "JAVAC_EXIT=$RESULT"
    echo 'TARGET=official Mojang Minecraft 26.4 Snapshot 3 with original BCCE layered source'
    echo 'SOURCE=original BCCE 8.0.23 + 3 documented registry/constructor seam substitutions'
    echo "NEOFORGE_MISSING=$(grep -c 'package net.neoforged' "$ERRORS" || true)"
    echo "IMPORT_ERRORS=$(grep -c 'error: package .* does not exist' "$ERRORS" || true)"
    echo "MISSING_SYMBOLS=$(grep -c 'error: cannot find symbol' "$ERRORS" || true)"
    echo 'FIRST_COMPILER_ERRORS:'
    grep -E 'error:|symbol:|location:|cannot access|module not found' "$ERRORS" | head -n 55 || true
    echo 'NEXT=port NeoForge capability/render hooks and BCCE base tile dependency closure'
  } > "$REPORT"
  echo 'BLOCKED: original pipe-holder classes do NOT compile against current HOWL/Mojang classpath.'
fi
cat "$REPORT"
echo 'Full exact javac diagnostics saved as pipeline artifact, not hidden.'
# No JAR, mod metadata or release is produced for a blocked compiler build.
