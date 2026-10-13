#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
API_JAR="${1:-lib/howl-api-sorting-pipes.jar}"
bash build.sh "$API_JAR"
mkdir -p build/tests
mapfile -d '' TESTS < <(find tests -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp "$API_JAR:dist/howl-sorting-pipes-1.0.2.jar" -d build/tests "${TESTS[@]}"
java -cp "build/tests:$API_JAR:dist/howl-sorting-pipes-1.0.2.jar" dev.howlingwhispers.sortingpipes.SortingTransportTest

java -cp "build/tests:$API_JAR:dist/howl-sorting-pipes-1.0.2.jar" dev.howlingwhispers.sortingpipes.SortingCommandTest
