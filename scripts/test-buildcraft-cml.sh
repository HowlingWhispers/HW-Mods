#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac --release 21 -encoding UTF-8 -d "$TMP_DIR" \
  mods/buildcraft-cml/src/dev/howlingwhispers/buildcraft/PipeNetwork.java \
  tests/buildcraft-cml/PipeNetworkTest.java
java -cp "$TMP_DIR" PipeNetworkTest
