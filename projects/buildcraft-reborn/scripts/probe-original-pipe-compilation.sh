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
# Pull Mojang's EXACT SHA-1-authenticated support libraries so missing
# JOML/Authlib/Netty do not obscure actual NeoForge/API port blockers.
LIBROOT="$BASE/mojang-26.4-snapshot-3/libraries"
mkdir -p "$LIBROOT"
jq -r '.libraries[].downloads.artifact | select(.url != null) |
  [.url, .path, .sha1] | join("|")' \
  "$BASE/mojang-26.4-snapshot-3/version.json" > "$BASE/mojang-libraries-list.txt"
while IFS='|' read -r url artifact expected; do
  [[ "$artifact" != /* && "$artifact" != *".."* && "$expected" =~ ^[0-9a-f]{40}$ ]] ||
    { echo "Mojang library entry unsafe" >&2; exit 1; }
  target="$LIBROOT/$artifact"
  mkdir -p "$(dirname "$target")"
  if [[ ! -s "$target" ]]; then
    curl --fail --location --silent --show-error --retry 3 "$url" -o "$target.tmp"
    mv "$target.tmp" "$target"
  fi
  [[ "$(sha1sum "$target" | awk '{print $1}')" == "$expected" ]] ||
    { echo "Mojang library checksum mismatch: $artifact" >&2; exit 1; }
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
    echo "OFFICIAL_MOJANG_LIBRARIES=$(find "$LIBROOT" -name '*.jar' | wc -l)"
    echo 'EXACT_MOJANG_API_SOUND_CLASSES:'
    jar tf "$CLIENT" | grep -Ei '/(BlockSoundSet|SoundSet|SoundType|BlocksSound|BlockSounds)\\.class
    echo 'TARGET=official Mojang Minecraft 26.4 Snapshot 3 with original BCCE layered source'
    echo 'SOURCE=original BCCE 8.0.23 + 3 documented registry/constructor seam substitutions'
    echo "NEOFORGE_MISSING=$(grep -c 'package net.neoforged' "$ERRORS" || true)"
    echo "IMPORT_ERRORS=$(grep -c 'error: package .* does not exist' "$ERRORS" || true)"
    echo "MISSING_SYMBOLS=$(grep -c 'error: cannot find symbol' "$ERRORS" || true)"
    echo 'FIRST_COMPILER_ERRORS:'
    awk '/error:|symbol:|location:|cannot access|module not found/ {print; if (++n == 55) exit}' "$ERRORS" || true
    echo 'NEXT=port NeoForge capability/render hooks and BCCE base tile dependency closure'
  } > "$REPORT"
  echo 'BLOCKED: original pipe-holder classes do NOT compile against current HOWL/Mojang classpath.'
fi
cat "$REPORT"
echo 'Full exact javac diagnostics saved as pipeline artifact, not hidden.'
# No JAR, mod metadata or release is produced for a blocked compiler build.
 || true
    echo 'EXACT_MOJANG_BLOCK_SOUND_PROPERTIES:'
    javap -p -classpath "$CLASSPATH" 'net.minecraft.world.level.block.state.BlockBehaviour$Properties' 2>/dev/null |
      grep -iE 'sound|Properties' | head -n 22 || true
    echo 'SOUND_REGISTRY_TYPES:'
    javap -p -classpath "$CLASSPATH" net.minecraft.core.registries.BuiltInRegistries 2>/dev/null |
      grep -i 'SOUND' | head -n 12 || true
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
