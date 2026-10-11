#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="0.1.0-dev.1"
OUT="out/hw-waypoints"
[[ -f "dist/hw-waypoints-${VERSION}.jar" ]] || { echo 'Build HW Waypoints first.' >&2; exit 1; }
if [[ -n "${HW_CODALOADER_API_JAR:-}" ]]; then
  API_CP="$HW_CODALOADER_API_JAR"
else
  API_CP="$OUT/api"
fi
mkdir -p "$OUT/tests"
javac --release 21 -encoding UTF-8 -cp "$API_CP:dist/hw-waypoints-${VERSION}.jar" \
  -d "$OUT/tests" tests/waypoints/WaypointsTest.java
java -cp "$OUT/tests:$API_CP:dist/hw-waypoints-${VERSION}.jar" dev.howlingwhispers.waypoints.WaypointsTest
