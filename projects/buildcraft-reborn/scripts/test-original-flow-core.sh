#!/usr/bin/env bash
# Compile exact BCCE original code used by PipeFlowItems, no substituted logic.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PORT="$ROOT/projects/buildcraft-reborn"
EFFECTIVE="$ROOT/dist/buildcraft-reborn/effective-1.21.11-neoforge/src/main/java"
CACHE="$ROOT/dist/buildcraft-reborn/test-deps"
JAR="$CACHE/guava-33.4.8-jre.jar"
URL="https://repo.maven.apache.org/maven2/com/google/guava/guava/33.4.8-jre"
mkdir -p "$CACHE"
if [[ ! -s "$JAR" ]]; then
  curl --fail --location --silent --show-error --retry 2 --output "$JAR.tmp" "$URL/guava-33.4.8-jre.jar"
  mv "$JAR.tmp" "$JAR"
fi
curl --fail --location --silent --show-error --retry 2 --output "$JAR.sha256.tmp" "$URL/guava-33.4.8-jre.jar.sha256"
CHECK="$(tr -d '\r\n ' < "$JAR.sha256.tmp")"
[[ "$CHECK" =~ ^[a-fA-F0-9]{64}$ ]] || { echo "Invalid Guava checksum" >&2; exit 1; }
printf '%s  %s\n' "$CHECK" "$JAR" | sha256sum -c -
mv "$JAR.sha256.tmp" "$JAR.sha256"
test -s "$EFFECTIVE/buildcraft/lib/misc/data/DelayedList.java"
test -s "$EFFECTIVE/buildcraft/api/v2/pipe/ItemTransportProfile.java"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
javac --release 21 -encoding UTF-8 -cp "$JAR" -d "$BUILD" \
  "$EFFECTIVE/buildcraft/lib/misc/data/DelayedList.java" \
  "$EFFECTIVE/buildcraft/api/v2/pipe/ItemTransportProfile.java" \
  "$PORT/tests/OriginalPipeSchedulingTest.java"
java -ea -cp "$JAR:$BUILD" OriginalPipeSchedulingTest
