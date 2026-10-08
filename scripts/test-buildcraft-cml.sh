#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac --release 21 -encoding UTF-8 -d "$TMP_DIR" \
  mods/buildcraft-cml/src/dev/howlingwhispers/buildcraft/PipeNetwork.java \
  mods/buildcraft-cml/src/dev/howlingwhispers/buildcraft/PipeNetworkStore.java \
  tests/buildcraft-cml/PipeNetworkTest.java \
  tests/buildcraft-cml/PipePersistenceTest.java \
  tests/buildcraft-cml/PipeWrenchTest.java
java -cp "$TMP_DIR" PipeNetworkTest
java -cp "$TMP_DIR" PipePersistenceTest
java -cp "$TMP_DIR" PipeWrenchTest
